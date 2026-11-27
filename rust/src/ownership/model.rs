use super::date::Date;
use serde::{Deserialize, Serialize};

pub const MM_PER_MILE:u64=1_609_344;
pub const MAX_DISTANCE:u64=MM_PER_MILE*3_000_000;
pub const MAX_RECORDS:usize=10_000;

#[derive(Clone,Debug,Default,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Garage {
    pub schema_version:u32,
    pub revision:u64,
    #[serde(default)] pub vehicles:Vec<Vehicle>,
    #[serde(default)] pub readings:Vec<Reading>,
    #[serde(default)] pub services:Vec<Service>,
    #[serde(default)] pub fuel:Vec<Fuel>,
    #[serde(default)] pub expenses:Vec<Expense>,
    #[serde(default)] pub trips:Vec<Trip>,
    #[serde(default)] pub overrides:Vec<Override>,
    #[serde(default)] pub custom_items:Vec<CustomItem>,
    #[serde(default)] pub preferences:MaintenancePreferences,
    #[serde(default)] pub snoozes:Vec<ReminderSnooze>,
}
impl Garage {pub fn empty()->Self{Self{schema_version:1,..Self::default()}}}

#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Vehicle {
    pub id:String,pub nickname:String,pub make:String,pub model:String,pub year:u32,
    #[serde(default)] pub trim:String,#[serde(default)] pub engine:String,#[serde(default)] pub transmission:String,
    #[serde(default)] pub drivetrain:String,#[serde(default)] pub vin:Option<String>,#[serde(default)] pub plate:Option<String>,
    #[serde(default)] pub purchase_date:Option<Date>,#[serde(default)] pub purchase_mileage_mm:Option<u64>,#[serde(default)] pub purchase_price_cents:Option<u64>,
    #[serde(default)] pub in_service_date:Option<Date>,
    #[serde(default="usd")] pub currency:String,
    #[serde(default="us")] pub units:String,
    #[serde(default)] pub maintenance_profile:Option<String>,
    #[serde(default)] pub severe:bool,#[serde(default)] pub archived:bool,#[serde(default)] pub notes:String,
}
fn usd()->String{"USD".into()}fn us()->String{"us".into()}

#[derive(Clone,Debug,PartialEq,Eq,Serialize,Deserialize)]
#[serde(rename_all="camelCase")]
pub enum Confidence { Confirmed, Approximate }
#[derive(Clone,Debug,PartialEq,Eq,Serialize,Deserialize)]
#[serde(rename_all="camelCase")]
pub enum DistanceSource { Manual, Gps, Obd, Starlink, Import }
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Reading {
    pub id:String,pub vehicle_id:String,pub at_ms:u64,pub distance_mm:u64,pub source:DistanceSource,pub confidence:Confidence,
    #[serde(default)] pub supersedes:Option<String>,#[serde(default)] pub notes:String,
}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct DistanceRange {pub low_mm:u64,pub high_mm:u64}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Service {
    pub id:String,pub vehicle_id:String,pub item_ids:Vec<String>,
    #[serde(default)] pub date:Option<Date>,#[serde(default)] pub mileage:Option<DistanceRange>,
    #[serde(default)] pub cost_cents:u64,#[serde(default)] pub provider:String,#[serde(default)] pub notes:String,
    #[serde(default)] pub factory_baseline:bool,
    #[serde(default)] pub cost_breakdown:Option<CostBreakdown>,
    #[serde(default)] pub diy:bool,
    #[serde(default)] pub part_numbers:Vec<String>,
    #[serde(default)] pub warranty:String,
}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct CostBreakdown {pub parts_cents:u64,pub labor_cents:u64,pub tax_cents:u64}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Fuel {
    pub id:String,pub vehicle_id:String,pub at_ms:u64,pub distance_mm:u64,pub volume_ml:u64,pub cost_cents:u64,pub full:bool,
    #[serde(default)] pub missed_fill:bool,#[serde(default)] pub notes:String,
}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Expense {
    pub id:String,pub vehicle_id:String,pub date:Date,pub category:String,pub cost_cents:u64,
    #[serde(default)] pub financing:bool,#[serde(default)] pub notes:String,
}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Trip {
    pub id:String,pub vehicle_id:String,pub started_ms:u64,pub ended_ms:u64,pub distance_mm:u64,
    pub source:DistanceSource,#[serde(default)]pub notes:String,
}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Interval {pub miles:Option<u64>,pub months:Option<u32>}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Override {pub vehicle_id:String,pub item_id:String,pub interval:Interval,#[serde(default)]pub disabled:bool}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct CustomItem {pub id:String,pub vehicle_id:String,pub title:String,pub action:String,pub interval:Interval,#[serde(default)]pub notes:String}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct MaintenancePreferences {pub due_soon_mm:u64,pub due_soon_days:i64}
impl Default for MaintenancePreferences {fn default()->Self{Self{due_soon_mm:500*MM_PER_MILE,due_soon_days:30}}}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct ReminderSnooze {pub vehicle_id:String,pub item_id:String,pub until:Date}
#[derive(Clone,Debug,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Rule {
    pub id:String,pub title:String,pub action:String,pub normal:Interval,
    #[serde(default)]pub initial:Option<Interval>,#[serde(default)]pub severe:Option<Interval>,
    pub source_row:String,#[serde(default)]pub note_refs:Vec<u32>,
}
#[derive(Clone,Debug,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Schedule {
    pub schema_version:u32,pub profile:String,pub market:String,pub vehicle:String,pub source_url:String,pub source_sha256:String,
    pub source_page:String,pub verified_date:String,pub limitations:String,pub rules:Vec<Rule>,
}
