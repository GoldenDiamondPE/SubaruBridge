use crate::transport::Engine;
use std::{collections::BTreeMap, sync::{Mutex, OnceLock}, slice};

#[repr(C)]
pub struct CoreBuffer { pub data: *mut u8, pub len: usize, pub capacity: usize }
impl CoreBuffer {
    fn new(mut bytes: Vec<u8>) -> Self {
        let value = Self { data: bytes.as_mut_ptr(), len: bytes.len(), capacity: bytes.capacity() };
        std::mem::forget(bytes); value
    }
    fn empty() -> Self { Self::new(Vec::new()) }
}
struct Registry { next: u64, engines: BTreeMap<u64,Engine> }
static ENGINES: OnceLock<Mutex<Registry>> = OnceLock::new();
fn registry() -> &'static Mutex<Registry> { ENGINES.get_or_init(|| Mutex::new(Registry { next:1, engines:BTreeMap::new() })) }

#[no_mangle]
pub extern "C" fn sb_core_create(seed: u64) -> u64 {
    let Ok(mut r) = registry().lock() else { return 0; };
    if r.engines.len() >= 4 { return 0; }
    let id = r.next; r.next = r.next.wrapping_add(1); r.engines.insert(id,Engine::new(seed)); id
}
#[no_mangle]
pub extern "C" fn sb_core_destroy(handle: u64) { if let Ok(mut r) = registry().lock() { r.engines.remove(&handle); } }

/// Caller owns a valid readable pointer for `len` bytes for the duration of the call.
#[no_mangle]
pub unsafe extern "C" fn sb_core_feed(handle: u64, data: *const u8, len: usize, now: u64) -> CoreBuffer {
    if len > 65536 || (len != 0 && data.is_null()) { return CoreBuffer::empty(); }
    let input = if len == 0 { &[] } else { slice::from_raw_parts(data,len) };
    let Ok(mut r) = registry().lock() else { return CoreBuffer::empty(); };
    r.engines.get_mut(&handle).map(|e| CoreBuffer::new(e.feed(input,now).encode())).unwrap_or_else(CoreBuffer::empty)
}
#[no_mangle]
pub unsafe extern "C" fn sb_core_respond(handle: u64, connection: u64, data: *const u8, len: usize, close: u8, now: u64) -> CoreBuffer {
    if len > 65536 || (len != 0 && data.is_null()) { return CoreBuffer::empty(); }
    let input = if len == 0 { &[] } else { slice::from_raw_parts(data,len) };
    let Ok(mut r) = registry().lock() else { return CoreBuffer::empty(); };
    r.engines.get_mut(&handle).map(|e| CoreBuffer::new(e.respond(connection,input,close != 0,now).encode())).unwrap_or_else(CoreBuffer::empty)
}
#[no_mangle]
pub extern "C" fn sb_core_tick(handle: u64, now: u64) -> CoreBuffer {
    let Ok(mut r) = registry().lock() else { return CoreBuffer::empty(); };
    r.engines.get_mut(&handle).map(|e| CoreBuffer::new(e.tick(now).encode())).unwrap_or_else(CoreBuffer::empty)
}
/// Decode one bounded AMS entity notification without performing any I/O.
#[no_mangle]
pub unsafe extern "C" fn sb_ams_decode(data: *const u8, len: usize) -> CoreBuffer {
    if len > 512 || (len != 0 && data.is_null()) {
        return CoreBuffer::new(b"{\"ok\":false,\"error\":\"AMS Length\"}".to_vec());
    }
    let input = if len == 0 { &[] } else { slice::from_raw_parts(data, len) };
    CoreBuffer::new(crate::apple_media::decode_json(input))
}
/// Free exactly once, using the unchanged buffer returned by this library.
#[no_mangle]
pub unsafe extern "C" fn sb_core_buffer_free(buffer: CoreBuffer) {
    if buffer.capacity > 0 && !buffer.data.is_null() { drop(Vec::from_raw_parts(buffer.data,buffer.len,buffer.capacity)); }
}
