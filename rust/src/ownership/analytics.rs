//! Recorded ownership summaries. Native adapters provide actual local calendar boundaries.
use super::{date::Date,model::*,FuelSummary,fuel_entries};
use serde::{Serialize,Deserialize};
use std::collections::BTreeSet;

#[derive(Clone,Debug,Serialize,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Period {pub id:String,pub label:String,pub start_ms:i64,pub end_ms:i64,pub start_date:Date,pub end_date:Date}
impl Period {
    pub fn validate(&self)->Result<(),String>{
        let days=self.end_date.ordinal()-self.start_date.ordinal();
        if self.id.is_empty() || self.id.len()>80 || self.label.len()>80 || !(1..=366).contains(&days) || self.end_ms<=self.start_ms{return Err("Invalid analytics period".into());}
        let duration=self.end_ms.checked_sub(self.start_ms).ok_or("Analytics timestamp range overflow")? as u64;let nominal=days as u64*86_400_000;
        // Local boundaries may differ from fixed-day arithmetic across clock/zone changes.
        if duration.abs_diff(nominal)>2*86_400_000{return Err("Analytics calendar and timestamp bounds disagree".into());}Ok(())
    }
    fn time(&self,value:u64)->bool{(value as i128)>=self.start_ms as i128 && (value as i128)<self.end_ms as i128}
    fn date(&self,value:&Date)->bool{value>=&self.start_date && value<&self.end_date}
}
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub struct PeriodCosts {pub fuel_cents:u64,pub service_cents:u64,pub other_operating_cents:u64,pub financing_cents:u64,pub operating_cents:u64,pub total_cents:u64}
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub struct Summary {
    pub period:Period,pub recorded_trips:usize,pub trip_distance_mm:u64,pub trip_duration_ms:u64,pub longest_trip_mm:Option<u64>,pub average_trip_mm:Option<u64>,
    pub confirmed_readings:usize,pub confirmed_growth_mm:Option<u64>,pub growth_start_ms:Option<u64>,pub growth_end_ms:Option<u64>,
    pub fuel:FuelSummary,pub costs:PeriodCosts,pub service_visits:usize,pub undated_service_count:usize,pub undated_service_cents:u64,
}
pub fn summarize(state:&Garage,id:&str,period:&Period)->Summary {
    let trips:Vec<_>=state.trips.iter().filter(|t|t.vehicle_id==id && period.time(t.ended_ms)).collect();
    let trip_distance_mm=trips.iter().map(|v|v.distance_mm).sum::<u64>();let trip_duration_ms=trips.iter().map(|v|v.ended_ms-v.started_ms).sum();
    let replaced:BTreeSet<_>=state.readings.iter().filter_map(|v|v.supersedes.as_deref()).collect();
    let mut readings:Vec<_>=state.readings.iter().filter(|v|v.vehicle_id==id && v.confidence==Confidence::Confirmed && !replaced.contains(v.id.as_str()) && period.time(v.at_ms)).collect();
    readings.sort_by_key(|v|v.at_ms);
    let growth=if readings.len()>=2{Some(readings.last().unwrap().distance_mm-readings[0].distance_mm)}else{None};
    let fuel:Vec<_>=state.fuel.iter().filter(|v|v.vehicle_id==id && period.time(v.at_ms)).collect();
    let services:Vec<_>=state.services.iter().filter(|v|v.vehicle_id==id && !v.factory_baseline && v.date.as_ref().is_some_and(|d|period.date(d))).collect();
    let undated:Vec<_>=state.services.iter().filter(|v|v.vehicle_id==id && !v.factory_baseline && v.date.is_none()).collect();
    let expenses:Vec<_>=state.expenses.iter().filter(|v|v.vehicle_id==id && period.date(&v.date)).collect();
    let fuel_cost=fuel.iter().map(|v|v.cost_cents).sum::<u64>();let service_cost=services.iter().map(|v|v.cost_cents).sum::<u64>();
    let other=expenses.iter().filter(|v|!v.financing).map(|v|v.cost_cents).sum::<u64>();let financing=expenses.iter().filter(|v|v.financing).map(|v|v.cost_cents).sum::<u64>();
    Summary{period:period.clone(),recorded_trips:trips.len(),trip_distance_mm,trip_duration_ms,
        longest_trip_mm:trips.iter().map(|v|v.distance_mm).max(),average_trip_mm:if trips.is_empty(){None}else{Some(trip_distance_mm/trips.len() as u64)},
        confirmed_readings:readings.len(),confirmed_growth_mm:growth,growth_start_ms:if growth.is_some(){readings.first().map(|v|v.at_ms)}else{None},growth_end_ms:if growth.is_some(){readings.last().map(|v|v.at_ms)}else{None},
        fuel:fuel_entries(fuel),costs:PeriodCosts{fuel_cents:fuel_cost,service_cents:service_cost,other_operating_cents:other,financing_cents:financing,operating_cents:fuel_cost+service_cost+other,total_cents:fuel_cost+service_cost+other+financing},
        service_visits:services.len(),undated_service_count:undated.len(),undated_service_cents:undated.iter().map(|v|v.cost_cents).sum()}
}

/// Measured growth window and a labeled linear estimate. Never invents unrecorded trips.
pub fn forecast(state:&Garage,id:&str,now:u64)->serde_json::Value{
    let replaced:BTreeSet<_>=state.readings.iter().filter_map(|v|v.supersedes.as_deref()).collect();
    let mut readings:Vec<_>=state.readings.iter().filter(|v|v.vehicle_id==id && v.confidence==Confidence::Confirmed && !replaced.contains(v.id.as_str())).collect();readings.sort_by_key(|v|v.at_ms);
    let Some(first)=readings.first() else{return serde_json::Value::Null;};let last=readings.last().unwrap();
    let span=last.at_ms-first.at_ms;
    // Stale or implausible measurements must not drive an apparently precise forecast.
    if readings.len()<2 || span<86_400_000 || now.saturating_sub(last.at_ms)>90*86_400_000{return serde_json::Value::Null;}
    let daily=(last.distance_mm-first.distance_mm) as f64*86_400_000.0/span as f64;
    if daily<=0.0 || daily>1000.0*MM_PER_MILE as f64{return serde_json::Value::Null;}
    serde_json::json!({"estimated":true,"growthStartMs":first.at_ms,"growthEndMs":last.at_ms,"averageMmPerDay":daily,"annualizedMm":(daily*365.0).round() as u64,"method":"Linear estimate from active confirmed readings; does not establish complete trip coverage."})
}
