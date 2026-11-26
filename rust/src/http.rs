pub const HEADER_LIMIT: usize = 8192;
pub const BODY_LIMIT: usize = 4096;
#[derive(Debug, PartialEq, Eq)]
pub enum Frame { Incomplete, Complete(usize), Reject(u16) }

/// Delimits one request. Ambiguous lengths and transfer encodings are rejected.
pub fn request_frame(bytes: &[u8]) -> Frame {
    let end = bytes.windows(4).position(|v| v == b"\r\n\r\n");
    let Some(end) = end else { return if bytes.len() > HEADER_LIMIT { Frame::Reject(431) } else { Frame::Incomplete }; };
    if end + 4 > HEADER_LIMIT { return Frame::Reject(431); }
    let Ok(header) = std::str::from_utf8(&bytes[..end]) else { return Frame::Reject(400); };
    let mut lines = header.split("\r\n");
    let parts: Vec<_> = lines.next().unwrap_or("").split(' ').collect();
    if parts.len() != 3 || parts[0].is_empty() || parts[1].len() > 2048 || !(parts[2] == "HTTP/1.0" || parts[2] == "HTTP/1.1") { return Frame::Reject(400); }
    let mut length = None;
    for line in lines {
        let Some((name, value)) = line.split_once(':') else { return Frame::Reject(400); };
        if name.is_empty() || name.bytes().any(|b| !b.is_ascii_alphanumeric() && b != b'-') { return Frame::Reject(400); }
        if name.eq_ignore_ascii_case("transfer-encoding") { return Frame::Reject(400); }
        if name.eq_ignore_ascii_case("content-length") {
            let value = value.trim();
            if length.is_some() || value.is_empty() || !value.bytes().all(|b| b.is_ascii_digit()) { return Frame::Reject(400); }
            let Ok(n) = value.parse::<usize>() else { return Frame::Reject(413); };
            if n > BODY_LIMIT { return Frame::Reject(413); }
            length = Some(n);
        }
    }
    let total = end + 4 + length.unwrap_or(0);
    if bytes.len() < total { Frame::Incomplete } else { Frame::Complete(total) }
}

pub fn error(status: u16) -> Vec<u8> {
    format!("HTTP/1.1 {status} Rejected\r\nContent-Length: 0\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").into_bytes()
}
