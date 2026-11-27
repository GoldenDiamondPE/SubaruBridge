//! Platform-neutral STARLINK framing and bounded local IPv4/HTTP transport.
//! Bluetooth sockets, presentation, private journals and app lifecycle belong to adapters.
pub mod http;
pub mod slip;
pub mod transport;
pub mod ownership;
pub mod capabilities;
mod ffi;
