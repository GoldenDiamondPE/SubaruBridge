use crate::{http, slip};
use std::collections::{BTreeMap, VecDeque};

const FIN: u8 = 1; const SYN: u8 = 2; const RST: u8 = 4; const ACK: u8 = 16;
const LOCAL: [u8; 4] = [192,168,1,1];
const FACTORY_PEER: [u8;4] = [192,168,0,1];
const BENCH_PEER: [u8;4] = [192,168,1,2];
const RECEIVE_WINDOW: usize = 16384;

#[derive(Default)]
pub struct Batch { pub wire: Vec<u8>, pub requests: Vec<(u64, Vec<u8>)> }
impl Batch {
    fn send(&mut self, packet: Vec<u8>) { self.wire.extend(slip::encode(&packet)); }
    pub fn encode(self) -> Vec<u8> {
        let mut result = Vec::new();
        result.extend((self.wire.len() as u32).to_be_bytes()); result.extend(self.wire);
        result.extend((self.requests.len() as u16).to_be_bytes());
        for (id, request) in self.requests { result.extend(id.to_be_bytes()); result.extend((request.len() as u32).to_be_bytes()); result.extend(request); }
        result
    }
}

pub fn checksum(data: &[u8]) -> u16 {
    let mut sum = 0u32;
    for chunk in data.chunks(2) { sum += ((chunk[0] as u32) << 8) | chunk.get(1).copied().unwrap_or(0) as u32; }
    while sum >> 16 != 0 { sum = (sum & 65535) + (sum >> 16); }
    !(sum as u16)
}
pub fn ipv4(protocol: u8, source: [u8;4], target: [u8;4], payload: &[u8]) -> Vec<u8> {
    let mut packet = vec![0x45,0,0,0,0,0,0x40,0,64,protocol,0,0];
    packet.extend(source); packet.extend(target); packet.extend(payload);
    let length = packet.len() as u16; packet[2..4].copy_from_slice(&length.to_be_bytes());
    let cs = checksum(&packet[..20]); packet[10..12].copy_from_slice(&cs.to_be_bytes()); packet
}
fn tcp(peer:[u8;4],port: u16, seq: u32, ack: u32, flags: u8, payload: &[u8]) -> Vec<u8> {
    let mut segment = Vec::new(); segment.extend(80u16.to_be_bytes()); segment.extend(port.to_be_bytes());
    segment.extend(seq.to_be_bytes()); segment.extend(ack.to_be_bytes());
    segment.extend([if flags & SYN != 0 { 6 << 4 } else { 5 << 4 },flags]);
    segment.extend((RECEIVE_WINDOW as u16).to_be_bytes()); segment.extend([0,0,0,0]);
    if flags & SYN != 0 { segment.extend([2,4,0x04,0xb0]); }
    segment.extend(payload);
    let mut pseudo = Vec::new(); pseudo.extend(LOCAL); pseudo.extend(peer); pseudo.extend([0,6]); pseudo.extend((segment.len() as u16).to_be_bytes()); pseudo.extend(&segment);
    let cs = checksum(&pseudo); segment[16..18].copy_from_slice(&cs.to_be_bytes()); ipv4(6,LOCAL,peer,&segment)
}
fn u16_at(data: &[u8], p: usize) -> u16 { u16::from_be_bytes([data[p],data[p+1]]) }
fn u32_at(data: &[u8], p: usize) -> u32 { u32::from_be_bytes([data[p],data[p+1],data[p+2],data[p+3]]) }

struct Sent { seq: u32, flags: u8, payload: Vec<u8>, at: u64, retries: u8 }
impl Sent { fn end(&self) -> u32 { self.seq.wrapping_add(self.payload.len() as u32).wrapping_add((self.flags & (SYN|FIN) != 0) as u32) } }
struct Reply { bytes: VecDeque<u8>, close: bool }
struct Connection {
    peer:[u8;4],
    id: u64, port: u16, initial: u32, receive: u32, send: u32, established: bool,
    outstanding: Option<Sent>, reply: Option<Reply>, input: Vec<u8>, pending: bool,
    pending_at: u64, last: u64, mss: usize, window: usize, reordered: BTreeMap<u32,Vec<u8>>,
    peer_fin: Option<u32>, received_fin: bool, sent_fin: bool, fin_acked: bool, dead: bool,
}
impl Connection {
    fn ack(&self, out: &mut Batch) { out.send(tcp(self.peer,self.port,self.send,self.receive,ACK,&[])); }
    fn tracked(&mut self, flags: u8, payload: Vec<u8>, now: u64, out: &mut Batch) {
        let sent = Sent { seq: self.send, flags, payload, at: now, retries: 0 };
        out.send(tcp(self.peer,self.port,sent.seq,self.receive,flags,&sent.payload)); self.send = sent.end(); self.outstanding = Some(sent);
    }
    fn consume(&mut self, seq: u32, data: &[u8]) {
        let behind = self.receive.wrapping_sub(seq);
        let data = if seq == self.receive { data }
            else if behind < (1u32 << 31) { if behind as usize >= data.len() { return; } else { &data[behind as usize..] } }
            else {
                if seq.wrapping_sub(self.receive) <= RECEIVE_WINDOW as u32 && self.reordered.len() < 8
                    && self.reordered.values().map(Vec::len).sum::<usize>() + data.len() <= RECEIVE_WINDOW {
                    self.reordered.entry(seq).or_insert_with(|| data.to_vec());
                }
                return;
            };
        if self.input.len() + data.len() > RECEIVE_WINDOW { return; }
        self.input.extend(data); self.receive = self.receive.wrapping_add(data.len() as u32);
        loop {
            // Retransmissions may overlap buffered data rather than begin exactly
            // at receive. Consume only the unseen suffix; discard stale copies.
            let key = self.reordered.keys().copied().find(|s| self.receive.wrapping_sub(*s) < (1u32 << 31));
            let Some(key) = key else { break; };
            let bytes = self.reordered.remove(&key).unwrap();
            let skip = (self.receive.wrapping_sub(key) as usize).min(bytes.len());
            let unseen = &bytes[skip..];
            if self.input.len() + unseen.len() > RECEIVE_WINDOW { break; }
            self.receive = self.receive.wrapping_add(unseen.len() as u32); self.input.extend(unseen);
        }
    }
    fn input(&mut self, segment: &[u8], now: u64, out: &mut Batch) {
        let flags = segment[13]; let seq = u32_at(segment,4); let ack = u32_at(segment,8);
        if flags & RST != 0 { self.dead = true; return; }
        if flags & SYN != 0 {
            if seq == self.initial { if let Some(sent) = &self.outstanding { out.send(tcp(self.peer,self.port,sent.seq,self.receive,sent.flags,&sent.payload)); } }
            return;
        }
        if flags & ACK == 0 { return; }
        self.last = now; self.window = u16_at(segment,14) as usize;
        if flags & ACK != 0 {
            if self.outstanding.as_ref().is_some_and(|v| v.end() == ack) {
                let sent = self.outstanding.take().unwrap();
                if sent.flags & SYN != 0 { self.established = true; }
                if sent.flags & FIN != 0 { self.fin_acked = true; }
            }
        }
        if !self.established { return; }
        let offset = ((segment[12] >> 4) as usize) * 4; let data = &segment[offset..];
        if !self.received_fin && !data.is_empty() { self.consume(seq,data); }
        if flags & FIN != 0 { self.peer_fin = Some(seq.wrapping_add(data.len() as u32)); }
        if !self.received_fin && self.peer_fin == Some(self.receive) { self.received_fin = true; self.receive = self.receive.wrapping_add(1); }
        if !data.is_empty() || flags & FIN != 0 { self.ack(out); }
        self.service(now,out);
    }
    fn service(&mut self, now: u64, out: &mut Batch) {
        if self.dead || !self.established || self.outstanding.is_some() { return; }
        if let Some(reply) = &mut self.reply {
            if !reply.bytes.is_empty() {
                if self.window == 0 { return; }
                let length = self.mss.min(self.window).min(reply.bytes.len());
                let payload = reply.bytes.drain(..length).collect(); self.tracked(ACK|8,payload,now,out); return;
            }
            let close = reply.close; self.reply = None;
            if close || self.received_fin { if !self.sent_fin { self.sent_fin = true; self.tracked(FIN|ACK,Vec::new(),now,out); } return; }
        }
        if !self.pending && !self.sent_fin {
            match http::request_frame(&self.input) {
                http::Frame::Complete(length) => { let request = self.input.drain(..length).collect(); self.pending = true; self.pending_at = now; out.requests.push((self.id,request)); }
                http::Frame::Reject(code) => { self.input.clear(); self.reply = Some(Reply { bytes: http::error(code).into(), close: true }); self.service(now,out); }
                http::Frame::Incomplete => { if self.received_fin { self.sent_fin = true; self.tracked(FIN|ACK,Vec::new(),now,out); } }
            }
        }
    }
    fn tick(&mut self, now: u64, out: &mut Batch) {
        if now.saturating_sub(self.last) > 180000 || (self.pending && now.saturating_sub(self.pending_at) > 10000) { self.dead = true; return; }
        if let Some(sent) = &mut self.outstanding {
            if now.saturating_sub(sent.at) >= 400 * (1u64 << sent.retries.min(3)) {
                if sent.retries >= 5 { self.dead = true; return; }
                sent.at = now; sent.retries += 1; out.send(tcp(self.peer,self.port,sent.seq,self.receive,sent.flags,&sent.payload));
            }
        }
        self.service(now,out);
    }
}

struct Closed { send: u32, receive: u32, until: u64 }
pub struct Engine { decoder: slip::Decoder, peer:Option<[u8;4]>, connections: BTreeMap<u16,Connection>, closed: BTreeMap<u16,Closed>, next_id: u64, random: u64 }
impl Engine {
    pub fn new(seed: u64) -> Self { Self { decoder: slip::Decoder::default(), peer:None, connections: BTreeMap::new(), closed: BTreeMap::new(), next_id: 1, random: seed | 1 } }
    fn reap(&mut self, now: u64) {
        self.closed.retain(|_,c| now < c.until);
        let finished: Vec<_> = self.connections.iter().filter(|(_,c)| c.dead || (c.fin_acked && c.received_fin)).map(|(p,_)| *p).collect();
        for port in finished {
            let c = self.connections.remove(&port).unwrap();
            if c.fin_acked && c.received_fin {
                if self.closed.len() >= 64 {
                    if let Some(oldest) = self.closed.iter().min_by_key(|(_,v)| v.until).map(|(p,_)| *p) { self.closed.remove(&oldest); }
                }
                self.closed.insert(port,Closed { send:c.send, receive:c.receive, until:now.saturating_add(30000) });
            }
        }
    }
    pub fn feed(&mut self, bytes: &[u8], now: u64) -> Batch {
        let mut out = Batch::default();
        for packet in self.decoder.feed(bytes) { self.packet(&packet,now,&mut out); }
        out
    }
    fn packet(&mut self, packet: &[u8], now: u64, out: &mut Batch) {
        self.reap(now);
        if packet.len() < 20 || packet[0] >> 4 != 4 { return; }
        let header = ((packet[0] & 15) as usize)*4;
        if header < 20 || header > packet.len() || u16_at(packet,2) as usize != packet.len()
            || u16_at(packet,6) & 0x3fff != 0 || checksum(&packet[..header]) != 0
            || packet[16..20] != LOCAL { return; }
        let peer:[u8;4]=packet[12..16].try_into().unwrap();
        // The factory address was confirmed in car captures; the second address
        // preserves the existing synthetic bench. Never learn arbitrary peers,
        // and never let another address replace one selected for this link.
        if (peer!=FACTORY_PEER && peer!=BENCH_PEER) || self.peer.is_some_and(|selected|selected!=peer){return;}
        let data = &packet[header..];
        if packet[9] == 1 {
            if data.len() >= 8 && data[0] == 8 && data[1] == 0 && checksum(data) == 0 {
                self.peer=Some(peer);
                let mut reply = data.to_vec(); reply[0] = 0; reply[2] = 0; reply[3] = 0;
                let cs = checksum(&reply); reply[2..4].copy_from_slice(&cs.to_be_bytes()); out.send(ipv4(1,LOCAL,peer,&reply));
            }
            return;
        }
        if packet[9] != 6 || data.len() < 20 || u16_at(data,2) != 80 { return; }
        let offset = ((data[12] >> 4) as usize)*4;
        if offset < 20 || offset > data.len() { return; }
        let mut pseudo = Vec::new(); pseudo.extend(peer); pseudo.extend(LOCAL); pseudo.extend([0,6]); pseudo.extend((data.len() as u16).to_be_bytes()); pseudo.extend(data);
        if checksum(&pseudo) != 0 { return; }
        let port = u16_at(data,0); if port == 0 { return; }
        if data[13] & SYN != 0 && data[13] & (ACK|RST|FIN) == 0 {
            self.closed.remove(&port);
            let sequence = u32_at(data,4);
            if self.connections.get(&port).is_some_and(|c| c.initial == sequence && !c.dead) {
                self.connections.get_mut(&port).unwrap().input(data,now,out); return;
            }
            if !self.connections.contains_key(&port) && self.connections.len() >= 16 { return; }
            let mut mss = 1200usize; let mut at = 20;
            while at < offset {
                if data[at] == 0 { break; }
                if data[at] == 1 { at += 1; continue; }
                if at+1 >= offset || data[at+1] < 2 || at+data[at+1] as usize > offset { return; }
                if data[at] == 2 && data[at+1] == 4 { mss = (u16_at(data,at+2) as usize).clamp(64,1200); }
                at += data[at+1] as usize;
            }
            self.random ^= self.random << 13; self.random ^= self.random >> 7; self.random ^= self.random << 17;
            self.peer=Some(peer);
            let mut c = Connection { peer,id:self.next_id, port, initial:sequence, receive:sequence.wrapping_add(1), send:self.random as u32,
                established:false, outstanding:None, reply:None, input:Vec::new(), pending:false, pending_at:now, last:now,
                mss, window:u16_at(data,14) as usize, reordered:BTreeMap::new(), peer_fin:None, received_fin:false, sent_fin:false, fin_acked:false, dead:false };
            self.next_id = self.next_id.wrapping_add(1); c.tracked(SYN|ACK,Vec::new(),now,out); self.connections.insert(port,c);
        } else if let Some(connection) = self.connections.get_mut(&port) { connection.input(data,now,out); }
        else if let Some(closed) = self.closed.get(&port) {
            if data[13] & FIN != 0 && u32_at(data,4).wrapping_add((data.len()-offset) as u32).wrapping_add(1) == closed.receive {
                out.send(tcp(peer,port,closed.send,closed.receive,ACK,&[]));
            }
        } else if data[13] & RST == 0 {
            self.peer=Some(peer);
            // A stale connection after an app restart should restart cleanly.
            if data[13] & ACK != 0 { out.send(tcp(peer,port,u32_at(data,8),0,RST,&[])); }
            else { out.send(tcp(peer,port,0,u32_at(data,4).wrapping_add((data.len()-offset) as u32).wrapping_add((data[13] & FIN != 0) as u32),RST|ACK,&[])); }
        }
        self.reap(now);
    }
    pub fn respond(&mut self, id: u64, response: &[u8], close: bool, now: u64) -> Batch {
        let mut out = Batch::default();
        if let Some(c) = self.connections.values_mut().find(|c| c.id == id && c.pending) {
            c.pending = false;
            c.reply = Some(Reply { bytes: if response.is_empty() || response.len() > 65536 { http::error(500) } else { response.to_vec() }.into(), close: close || response.len() > 65536 });
            c.service(now,&mut out);
        }
        out
    }
    pub fn tick(&mut self, now: u64) -> Batch {
        let mut out = Batch::default();
        for c in self.connections.values_mut() { c.tick(now,&mut out); }
        self.reap(now); out
    }
    pub fn connection_count(&self) -> usize { self.connections.len() }
}
