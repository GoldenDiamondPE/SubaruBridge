use serde::Serialize;
#[derive(Serialize)]
#[serde(rename_all="camelCase")]
pub enum Evidence { ComponentTested, BenchTested, HardwareVerificationRequired, PlatformRestricted, Planned }
#[derive(Serialize)]
#[serde(rename_all="camelCase")]
pub struct Capability {pub id:&'static str,pub platform:&'static str,pub evidence:Evidence,pub limit:&'static str}
pub fn registry()->Vec<Capability>{vec![
    Capability{id:"ownership-core",platform:"shared",evidence:Evidence::ComponentTested,limit:"Typed versioned records, conservative mileage/maintenance/fuel/cost calculations; no vehicle-health diagnosis"},
    Capability{id:"android-garage",platform:"android",evidence:Evidence::ComponentTested,limit:"Atomic local storage, native forms, manual trips and explicit export/import; no automatic GPS or live ECU data"},
    Capability{id:"ios-garage",platform:"ios",evidence:Evidence::ComponentTested,limit:"Native Garage, private atomic storage and Android-compatible backup use the same Rust calculations; signed-phone validation remains required"},
    Capability{id:"starlink-html",platform:"android",evidence:Evidence::HardwareVerificationRequired,limit:"Independent RFCOMM/SLIP/IP/HTTP is bench tested; Subaru result pending"},
    Capability{id:"headunit-animation",platform:"headUnit",evidence:Evidence::Planned,limit:"JavaScript and arbitrary graphics are not assumed; static HTML fallback remains"},
    Capability{id:"parked-state",platform:"headUnit",evidence:Evidence::HardwareVerificationRequired,limit:"No trusted parked signal; distracting features remain unavailable"},
    Capability{id:"vehicle-telemetry",platform:"shared",evidence:Evidence::HardwareVerificationRequired,limit:"No verified STARLINK odometer, ECU or steering-wheel data"},
    Capability{id:"ios-starlink",platform:"ios",evidence:Evidence::PlatformRestricted,limit:"Generic Android RFCOMM is not an iOS transport; authorized accessory investigation required"},
    Capability{id:"ios-notification-inbox",platform:"ios",evidence:Evidence::PlatformRestricted,limit:"Other apps' notifications are not available through UserNotifications; ANCS remains research"},
]}
