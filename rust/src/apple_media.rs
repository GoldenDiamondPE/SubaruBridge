//! Original, bounded decoder for Apple's public Media Service entity updates.
//! No Bluetooth I/O, player hooks, credentials, or audio processing.

#[derive(Debug, PartialEq)]
pub enum MediaError { Length, Field, Flags, Utf8, Value }

#[derive(Debug, PartialEq)]
pub struct EntityUpdate<'a> {
    pub entity: u8,
    pub attribute: u8,
    pub truncated: bool,
    pub value: &'a str,
}

fn number(value: &str) -> Result<f64, MediaError> {
    let n: f64 = value.parse().map_err(|_| MediaError::Value)?;
    if n.is_finite() { Ok(n) } else { Err(MediaError::Value) }
}

pub fn decode(input: &[u8]) -> Result<EntityUpdate<'_>, MediaError> {
    if !(3..=512).contains(&input.len()) { return Err(MediaError::Length); }
    let (entity, attribute, flags) = (input[0], input[1], input[2]);
    if !matches!((entity, attribute), (0, 0..=2) | (1..=2, 0..=3)) { return Err(MediaError::Field); }
    if flags & !1 != 0 { return Err(MediaError::Flags); }
    let truncated = flags & 1 != 0;
    let value = match std::str::from_utf8(&input[3..]) {
        Ok(s) => s,
        // A marked truncation may cut a UTF-8 code point at the end of a value.
        Err(e) if truncated && e.error_len().is_none() =>
            std::str::from_utf8(&input[3..3 + e.valid_up_to()]).map_err(|_| MediaError::Utf8)?,
        Err(_) => return Err(MediaError::Utf8),
    };
    let update = EntityUpdate { entity, attribute, truncated, value };
    if !truncated { update.validate_value()?; }
    Ok(update)
}

impl EntityUpdate<'_> {
    fn validate_value(&self) -> Result<(), MediaError> {
        match (self.entity, self.attribute) {
            (0, 1) => { self.playback()?; }
            (0, 2) => { if !(0.0..=1.0).contains(&number(self.value)?) { return Err(MediaError::Value); } }
            (1, 0..=1) => { self.value.parse::<u32>().map_err(|_| MediaError::Value)?; }
            (1, 2..=3) => {
                if self.value.parse::<u8>().map_err(|_| MediaError::Value)? > 2 { return Err(MediaError::Value); }
            }
            (2, 3) => { if number(self.value)? < 0.0 { return Err(MediaError::Value); } }
            _ => (),
        }
        Ok(())
    }
    fn playback(&self) -> Result<(u8, f64, f64), MediaError> {
        let mut fields = self.value.split(',');
        let state: u8 = fields.next().ok_or(MediaError::Value)?.parse().map_err(|_| MediaError::Value)?;
        let rate = number(fields.next().ok_or(MediaError::Value)?)?;
        let elapsed = number(fields.next().ok_or(MediaError::Value)?)?;
        if fields.next().is_some() || state > 3 || elapsed < 0.0 { return Err(MediaError::Value); }
        Ok((state, rate, elapsed))
    }
    pub fn json(&self) -> String {
        let mut out = format!("{{\"ok\":true,\"version\":1,\"entity\":{},\"attribute\":{},\"truncated\":{},\"value\":{}",
            self.entity, self.attribute, self.truncated, quote(self.value));
        if !self.truncated && (self.entity, self.attribute) == (0, 1) {
            if let Ok((state, rate, elapsed)) = self.playback() {
                out.push_str(&format!(",\"state\":{state},\"rate\":{rate},\"elapsed\":{elapsed}"));
            }
        }
        out.push('}'); out
    }
}

fn quote(value: &str) -> String {
    use std::fmt::Write;
    let mut out = String::from("\"");
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""), '\\' => out.push_str("\\\\"),
            '\u{0000}'..='\u{001f}' => { let _ = write!(out, "\\u{:04x}", ch as u32); }
            _ => out.push(ch),
        }
    }
    out.push('"'); out
}

pub fn decode_json(input: &[u8]) -> Vec<u8> {
    match decode(input) {
        Ok(update) => update.json().into_bytes(),
        Err(e) => format!("{{\"ok\":false,\"error\":\"AMS {e:?}\"}}").into_bytes(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn packet(entity: u8, attribute: u8, value: &str) -> Vec<u8> {
        let mut p = vec![entity, attribute, 0]; p.extend_from_slice(value.as_bytes()); p
    }
    #[test] fn player_and_track_updates() {
        let p = packet(0, 0, "Taurus"); assert_eq!(decode(&p).unwrap().value, "Taurus");
        let p = packet(2, 2, "Original test title"); assert_eq!(decode(&p).unwrap().attribute, 2);
        let p = packet(0, 1, "1,1.0,12.5"); assert_eq!(decode(&p).unwrap().playback().unwrap(), (1, 1.0, 12.5));
    }
    #[test] fn rejects_malformed_headers_and_limits() {
        for p in [vec![], vec![0, 0], vec![3, 0, 0], vec![2, 4, 0], vec![0, 0, 2], vec![0; 513]] { assert!(decode(&p).is_err()); }
        assert!(decode(&vec![0; 512]).is_ok());
    }
    #[test] fn rejects_invalid_or_nonfinite_playback() {
        for value in ["", "1", "1,1", "1,1,2,3", "4,1,0", "1,NaN,0", "1,1,inf", "1,1,-1", "1,1,1e999"] {
            assert!(decode(&packet(0, 1, value)).is_err(), "{value}");
        }
        assert!(decode(&packet(0, 1, "2,-1,3")).is_ok());
    }
    #[test] fn numeric_fields_are_bounded_and_typed() {
        for (e, a, v) in [(0, 2, "1.1"), (0, 2, "-0.1"), (1, 0, "-1"), (1, 1, "4294967296"), (1, 2, "3"), (1, 3, "2.0"), (2, 3, "-1")] {
            assert!(decode(&packet(e, a, v)).is_err());
        }
        for (e, a, v) in [(0, 2, "0.5"), (1, 0, "0"), (1, 1, "42"), (1, 2, "2"), (2, 3, "183.5")] {
            assert!(decode(&packet(e, a, v)).is_ok());
        }
    }
    #[test] fn truncation_is_explicit_and_never_claims_numeric_state() {
        let p = [0, 1, 1, b'1', b','];
        let u = decode(&p).unwrap(); assert!(u.truncated); assert!(!u.json().contains("\"state\""));
    }
    #[test] fn only_a_marked_incomplete_utf8_suffix_is_tolerated() {
        assert!(decode(&[2, 2, 0, b'a', 0xc3]).is_err());
        assert_eq!(decode(&[2, 2, 1, b'a', 0xc3]).unwrap().value, "a");
        assert!(decode(&[2, 2, 1, 0xff, b'a']).is_err());
        assert_eq!(decode(&packet(2, 2, "Café 🚗")).unwrap().value, "Café 🚗");
    }
    #[test] fn json_escapes_controls_without_rust_only_unicode_syntax() {
        let p = packet(2, 2, "\"\\\n\0🚗");
        let out = String::from_utf8(decode_json(&p)).unwrap();
        assert!(out.contains("\\\"\\\\\\u000a\\u0000🚗")); assert!(!out.contains("\\u{"));
        assert!(String::from_utf8(decode_json(&[])).unwrap().contains("\"ok\":false"));
    }
    #[test] fn arbitrary_small_frames_do_not_panic() {
        for byte in 0..=255 { for len in 0..=20 { let _ = decode_json(&vec![byte; len]); } }
    }
}
