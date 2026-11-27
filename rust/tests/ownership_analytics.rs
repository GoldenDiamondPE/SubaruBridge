use subarubridge_core::ownership::{self as own,analytics::{self,Period},date::Date,model::*};
use serde_json::{json,Value};
fn day(s:&str)->Date{Date::parse(s).unwrap()}
fn stamp(s:&str)->u64{((day(s).ordinal()-day("1970-01-01").ordinal())*86_400_000) as u64}
fn period()->Period{Period{id:"november".into(),label:"November".into(),start_ms:stamp("2026-11-01") as i64,end_ms:stamp("2026-12-01") as i64,start_date:day("2026-11-01"),end_date:day("2026-12-01")}}
fn state()->Garage{serde_json::from_value(json!({"schemaVersion":1,"revision":7,"vehicles":[{"id":"car","nickname":"Synthetic","make":"Subaru","model":"Legacy","year":2017}]})).unwrap()}
fn run(s:&Garage,periods:Value)->Value{serde_json::from_slice(&own::execute_json(&serde_json::to_vec(&json!({"state":s,"today":"2026-11-27","nowMs":1800000000000u64,"command":{"type":"analyze","periods":periods}})).unwrap())).unwrap()}
#[test]fn empty_history_has_no_invented_mileage_or_fuel_measurement(){
 let s=state();let out=analytics::summarize(&s,"car",&period());assert_eq!(out.recorded_trips,0);assert_eq!(out.trip_distance_mm,0);assert!(out.average_trip_mm.is_none()&&out.confirmed_growth_mm.is_none()&&out.fuel.mpg_us.is_none());assert_eq!(out.costs.total_cents,0);
}
#[test]fn completion_time_and_exclusive_end_avoid_double_counting_trips(){
 let mut s=state();for(id,start,end,miles)in[("cross",stamp("2026-11-01")-1_800_000,stamp("2026-11-01")+1_800_000,15),("inside",stamp("2026-11-10"),stamp("2026-11-10")+3_600_000,10),("next",stamp("2026-12-01")-3_600_000,stamp("2026-12-01"),20)]{
  s.trips.push(Trip{id:id.into(),vehicle_id:"car".into(),started_ms:start,ended_ms:end,distance_mm:miles*MM_PER_MILE,source:DistanceSource::Manual,notes:String::new()});
 }let out=analytics::summarize(&s,"car",&period());assert_eq!(out.recorded_trips,2);assert_eq!(out.trip_distance_mm,25*MM_PER_MILE);assert_eq!(out.trip_duration_ms,7_200_000);assert_eq!(out.longest_trip_mm,Some(15*MM_PER_MILE));
}
#[test]fn confirmed_growth_excludes_replaced_and_approximate_readings(){
 let mut s=state();for(id,when,miles,confidence,supersedes)in[("old","2026-11-01",80000,Confidence::Confirmed,None),("fixed","2026-11-01",81000,Confidence::Confirmed,Some("old")),("last","2026-11-20",81600,Confidence::Confirmed,None),("estimate","2026-11-21",81700,Confidence::Approximate,None)]{
  s.readings.push(Reading{id:id.into(),vehicle_id:"car".into(),at_ms:stamp(when),distance_mm:miles*MM_PER_MILE,source:DistanceSource::Manual,confidence,supersedes:supersedes.map(str::to_owned),notes:"Explained correction".into()});
 }let out=analytics::summarize(&s,"car",&period());assert_eq!(out.confirmed_readings,2);assert_eq!(out.confirmed_growth_mm,Some(600*MM_PER_MILE));assert_eq!(out.growth_start_ms,Some(stamp("2026-11-01")));assert_eq!(out.growth_end_ms,Some(stamp("2026-11-20")));
}
#[test]fn dated_costs_financing_and_undated_services_are_distinct(){
 let mut s=state();for(id,date,cost)in[("dated",Some("2026-11-01"),10000),("undated",None,2500),("outside",Some("2026-12-01"),5000)]{
  s.services.push(serde_json::from_value(json!({"id":id,"vehicleId":"car","itemIds":["engine-oil"],"date":date,"mileage":{"lowMm":80000*MM_PER_MILE,"highMm":80000*MM_PER_MILE},"costCents":cost})).unwrap());
 }for(id,financing,cost)in[("parking",false,2000),("loan",true,3000)]{s.expenses.push(Expense{id:id.into(),vehicle_id:"car".into(),date:day("2026-11-10"),category:"Owner record".into(),cost_cents:cost,financing,notes:String::new()});}
 let out=analytics::summarize(&s,"car",&period());assert_eq!(out.service_visits,1);assert_eq!(out.undated_service_count,1);assert_eq!(out.undated_service_cents,2500);assert_eq!(out.costs.operating_cents,12000);assert_eq!(out.costs.financing_cents,3000);assert_eq!(out.costs.total_cents,15000);
}
#[test]fn fuel_cycles_require_both_full_fillups_inside_the_period(){
 let mut s=state();for(id,when,miles,volume,full)in[("before","2026-10-30",80000,40000,true),("anchor","2026-11-02",80300,40000,true),("partial","2026-11-08",80400,10000,false),("end","2026-11-20",80700,30000,true)]{
  s.fuel.push(Fuel{id:id.into(),vehicle_id:"car".into(),at_ms:stamp(when),distance_mm:miles*MM_PER_MILE,volume_ml:volume,cost_cents:1000,full,missed_fill:false,notes:String::new()});
 }let out=analytics::summarize(&s,"car",&period());assert_eq!(out.fuel.completed_cycles,1);assert_eq!(out.fuel.distance_mm,400*MM_PER_MILE);assert_eq!(out.fuel.volume_ml,40000);assert_eq!(out.costs.fuel_cents,3000);
}
#[test]fn analysis_is_read_only_and_checks_period_limits_and_clock_boundaries(){
 let s=state();let p=period();let out=run(&s,json!([p]));assert_eq!(out["ok"],true);assert_eq!(out["changed"],false);assert_eq!(out["state"]["revision"],7);assert_eq!(out["summaries"][0]["analytics"].as_array().unwrap().len(),1);
 assert_eq!(run(&s,json!([]))["ok"],false);assert_eq!(run(&s,json!([p,p]))["ok"],false);
 let mut dst=period();dst.end_ms+=3_600_000;assert!(dst.validate().is_ok());dst.end_ms+=3*86_400_000;assert!(dst.validate().is_err());
 let mut bad=period();bad.end_ms=bad.start_ms;assert!(bad.validate().is_err());assert_eq!(run(&s,json!(vec![period();25]))["ok"],false);
 let early=Period{id:"1970".into(),label:"January".into(),start_ms:-50_400_000,end_ms:31*86_400_000-50_400_000,start_date:day("1970-01-01"),end_date:day("1970-02-01")};assert!(early.validate().is_ok());
}
