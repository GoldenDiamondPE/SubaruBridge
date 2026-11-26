use subarubridge_core::{http::{self,Frame},slip::{self,Decoder},transport::{Engine,checksum,ipv4}};

fn peer(port: u16, seq: u32, ack: u32, flags:u8, payload:&[u8]) -> Vec<u8> {
    let mut t=Vec::new(); t.extend(port.to_be_bytes());t.extend(80u16.to_be_bytes());t.extend(seq.to_be_bytes());t.extend(ack.to_be_bytes());
    t.extend([5<<4,flags,0x20,0,0,0,0,0]); t.extend(payload);
    let mut p=vec![192,168,1,2,192,168,1,1,0,6];p.extend((t.len() as u16).to_be_bytes());p.extend(&t);
    let cs=checksum(&p);t[16..18].copy_from_slice(&cs.to_be_bytes());slip::encode(&ipv4(6,[192,168,1,2],[192,168,1,1],&t))
}
fn packets(bytes:&[u8])->Vec<Vec<u8>> { Decoder::default().feed(bytes) }
fn be32(data:&[u8],at:usize)->u32 { u32::from_be_bytes(data[at..at+4].try_into().unwrap()) }
fn handshake(engine:&mut Engine, port:u16, seq:u32)->(u32,u32) {
    let r=engine.feed(&peer(port,seq,0,2,&[]),0);let p=packets(&r.wire);assert_eq!(p.len(),1);assert_eq!(p[0][33],0x12);
    let server=be32(&p[0],24).wrapping_add(1);let client=seq.wrapping_add(1);
    assert!(engine.feed(&peer(port,client,server,16,&[]),1).requests.is_empty());(client,server)
}
fn collect(engine:&mut Engine,port:u16,client:u32,first:Vec<u8>)->(Vec<u8>,u32,bool) {
    let mut wire=first;let mut body=Vec::new();let mut next=0;let mut fin=false;
    for now in 2..500 {
        let mut follow=Vec::new();
        for p in packets(&wire) {
            let offset=20+((p[32]>>4) as usize)*4;
            let data=&p[offset..];body.extend(data);
            next=be32(&p,24).wrapping_add(data.len() as u32).wrapping_add((p[33]&1!=0) as u32);
            fin|=p[33]&1!=0;
            if !data.is_empty() || p[33]&1!=0 {follow.extend(engine.feed(&peer(port,client,next,16,&[]),now).wire);}
        }
        if follow.is_empty() {break;}wire=follow;
    }
    (body,next,fin)
}

#[test] fn slip_roundtrip_all_octets_and_splits() {
    let source:Vec<u8>=(0..=255).collect();let wire=slip::encode(&source);
    for split in 0..=wire.len() {let mut d=Decoder::default();let mut f=d.feed(&wire[..split]);f.extend(d.feed(&wire[split..]));assert_eq!(f,vec![source.clone()]);}
}
#[test] fn malformed_slip_recovers() {let mut d=Decoder::default();assert_eq!(d.feed(&[192,1,219,0,192,9,192]),vec![vec![9]]);}
#[test] fn oversized_slip_is_discarded() {let mut d=Decoder::default();let mut input=vec![7;5000];input.extend([192,3,192]);assert_eq!(d.feed(&input),vec![vec![3]]);}
#[test] fn fragmented_http_waits_for_body() {assert_eq!(http::request_frame(b"POST /x HTTP/1.1\r\nContent-Length: 4\r\n\r\nab"),Frame::Incomplete);}
#[test] fn ambiguous_headers_rejected() {
    for h in ["Content-Length: 1\r\nContent-Length: 1","Content-Length: -1","Transfer-Encoding: chunked","Content-Length: +3"] {
        assert_eq!(http::request_frame(format!("POST / HTTP/1.1\r\n{h}\r\n\r\na").as_bytes()),Frame::Reject(400));
    }
}
#[test] fn oversized_body_rejected() {assert_eq!(http::request_frame(b"POST / HTTP/1.1\r\nContent-Length: 5000\r\n\r\n"),Frame::Reject(413));}
#[test] fn oversized_header_rejected() {assert_eq!(http::request_frame(&vec![65;8193]),Frame::Reject(431));}
#[test] fn large_response_segments_and_fin() {
    let mut e=Engine::new(13);let port=50000;let (client,server)=handshake(&mut e,port,42);
    let req=b"GET / HTTP/1.0\r\n\r\n";let r=e.feed(&peer(port,client,server,24,req),2);assert_eq!(r.requests.len(),1);
    let expected=vec![b'A';28000];let r=e.respond(r.requests[0].0,&expected,true,3);
    let (body,_,fin)=collect(&mut e,port,client+req.len() as u32,r.wire);assert_eq!(body,expected);assert!(fin);
}
#[test] fn keep_alive_delivers_second_request() {
    let mut e=Engine::new(44);let port=50001;let (mut client,server)=handshake(&mut e,port,900);
    let req=b"GET /timestamp/ HTTP/1.1\r\nHost: 192.168.1.1\r\n\r\n";
    let first=e.feed(&peer(port,client,server,24,req),2);client+=req.len() as u32;
    let response=b"HTTP/1.1 200 OK\r\nContent-Length: 1\r\n\r\n0";
    let r=e.respond(first.requests[0].0,response,false,3);let (body,server,fin)=collect(&mut e,port,client,r.wire);assert_eq!(body,response);assert!(!fin);
    let next=e.feed(&peer(port,client,server,24,b"POST /local HTTP/1.1\r\nContent-Length: 1\r\n\r\nx"),10);assert_eq!(next.requests.len(),1);
}
#[test] fn duplicate_tcp_data_does_not_duplicate_request() {
    let mut e=Engine::new(45);let (client,server)=handshake(&mut e,50002,100);
    let data=peer(50002,client,server,24,b"GET / HTTP/1.0\r\n\r\n");assert_eq!(e.feed(&data,2).requests.len(),1);assert!(e.feed(&data,3).requests.is_empty());
}
#[test] fn reordered_segments_reassemble() {
    let mut e=Engine::new(3);let (client,server)=handshake(&mut e,50003,100);
    let req=b"GET / HTTP/1.0\r\n\r\n";
    assert!(e.feed(&peer(50003,client+5,server,24,&req[5..]),2).requests.is_empty());
    let result=e.feed(&peer(50003,client,server,24,&req[..5]),3);assert_eq!(result.requests[0].1,req);
}
#[test] fn overlapping_reordered_segments_reassemble() {
    let mut e=Engine::new(3);let (client,server)=handshake(&mut e,50007,100);
    let req=b"GET / HTTP/1.0\r\n\r\n";
    assert!(e.feed(&peer(50007,client+5,server,24,&req[5..]),2).requests.is_empty());
    let result=e.feed(&peer(50007,client,server,24,&req[..10]),3);assert_eq!(result.requests.len(),1);assert_eq!(result.requests[0].1,req);
}
#[test] fn completed_connections_free_active_capacity_and_ack_repeated_fin() {
    let mut e=Engine::new(8);
    for port in 51000..51032 {
        let(client,server)=handshake(&mut e,port,100);
        let req=b"GET / HTTP/1.0\r\n\r\n";
        let r=e.feed(&peer(port,client,server,24,req),2);
        let r=e.respond(r.requests[0].0,b"HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n",true,3);
        let(_,server,fin)=collect(&mut e,port,client+req.len() as u32,r.wire);assert!(fin);
        let close=peer(port,client+req.len() as u32,server,17,&[]);
        assert!(!e.feed(&close,4).wire.is_empty());
        assert_eq!(e.connection_count(),0);
        assert!(!e.feed(&close,5).wire.is_empty(),"Repeated FIN must still be acknowledged");
    }
}
#[test] fn checksum_errors_are_ignored() {let mut e=Engine::new(7);let mut data=peer(50004,0,0,2,&[]);data[12]^=1;assert!(e.feed(&data,0).wire.is_empty());}
#[test] fn retries_are_bounded() {let mut e=Engine::new(7);e.feed(&peer(50005,0,0,2,&[]),0);for time in (400..=30000).step_by(400) {e.tick(time);}assert_eq!(e.connection_count(),0);}
#[test] fn tcp_sequence_wrap() {let mut e=Engine::new(3);let(c,s)=handshake(&mut e,50006,u32::MAX-3);let r=e.feed(&peer(50006,c,s,24,b"GET / HTTP/1.0\r\n\r\n"),2);assert_eq!(r.requests.len(),1);}
#[test] fn connection_limit() {let mut e=Engine::new(2);for p in 1000..1030 {e.feed(&peer(p,1,0,2,&[]),0);}assert_eq!(e.connection_count(),16);}
#[test] fn fuzz_invalid_input_does_not_panic() {let mut e=Engine::new(13);let mut n=5u64;for _ in 0..5000 {n^=n<<13;n^=n>>7;n^=n<<17;e.feed(&n.to_le_bytes(),0);}assert_eq!(e.connection_count(),0);}
