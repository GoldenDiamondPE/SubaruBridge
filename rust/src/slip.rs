pub const END: u8 = 0xc0;
pub const ESC: u8 = 0xdb;

pub fn encode(bytes: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(bytes.len() + 2);
    out.push(END);
    for &b in bytes {
        match b { END => out.extend([ESC, 0xdc]), ESC => out.extend([ESC, 0xdd]), _ => out.push(b) }
    }
    out.push(END); out
}

#[derive(Default)]
pub struct Decoder { pending: Vec<u8>, escaped: bool, invalid: bool }
impl Decoder {
    pub fn feed(&mut self, bytes: &[u8]) -> Vec<Vec<u8>> {
        let mut frames = Vec::new();
        for &b in bytes {
            if b == END {
                if !self.invalid && !self.escaped && !self.pending.is_empty() { frames.push(std::mem::take(&mut self.pending)); }
                self.pending.clear(); self.escaped = false; self.invalid = false;
            } else if !self.invalid {
                if self.escaped {
                    self.escaped = false;
                    match b { 0xdc => self.pending.push(END), 0xdd => self.pending.push(ESC), _ => self.invalid = true }
                } else if b == ESC { self.escaped = true; }
                else { self.pending.push(b); }
                if self.pending.len() > 4096 { self.invalid = true; self.pending.clear(); }
            }
        }
        frames
    }
}
