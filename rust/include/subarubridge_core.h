#ifndef SUBARUBRIDGE_CORE_H
#define SUBARUBRIDGE_CORE_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct { uint8_t *data; size_t len; size_t capacity; } CoreBuffer;
uint64_t sb_core_create(uint64_t random_seed);
void sb_core_destroy(uint64_t handle);
CoreBuffer sb_core_feed(uint64_t handle, const uint8_t *data, size_t len, uint64_t monotonic_ms);
CoreBuffer sb_core_respond(uint64_t handle, uint64_t connection, const uint8_t *data, size_t len, uint8_t close, uint64_t monotonic_ms);
CoreBuffer sb_core_tick(uint64_t handle, uint64_t monotonic_ms);
void sb_core_buffer_free(CoreBuffer buffer);
/* Versioned UTF-8 JSON ownership request; maximum input 4 MiB. Return is an
 * owned JSON result, freed with sb_core_buffer_free. No I/O or vehicle access. */
CoreBuffer sb_ownership_execute(const uint8_t *data, size_t len);
/* Strict bounded UTF-8 JSON validation, optional UTF-8 BOM; 1 means valid.
 * Use before a platform DOM parser. No I/O; maximum input 4 MiB. */
uint8_t sb_ownership_json_valid(const uint8_t *data, size_t len);
/* Decode one AMS entity notification (3..512 bytes) to versioned UTF-8 JSON.
 * No Bluetooth I/O. Return is owned; free with sb_core_buffer_free. */
CoreBuffer sb_ams_decode(const uint8_t *data, size_t len);
/* Buffers are Rust-owned: read them, then free once with sb_core_buffer_free.
 * Packet batch: BE u32 wire length, SLIP bytes, BE u16 request count,
 * then (BE u64 connection, BE u32 request length, request bytes) per request.
 * No API opens sockets, requires Android/iOS, or reads private captures. */
#ifdef __cplusplus
}
#endif
#endif
