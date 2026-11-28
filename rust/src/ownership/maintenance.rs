use super::{date::Date,model::*};
use serde::Serialize;

#[derive(Clone,Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub struct Due {
    pub item_id:String,pub title:String,pub action:String,pub status:String,pub uncertain:bool,
    pub last_service_id:Option<String>,pub last_date:Option<Date>,pub last_mileage:Option<DistanceRange>,
    pub next_low_mm:Option<u64>,pub next_high_mm:Option<u64>,pub next_date:Option<Date>,
    pub remaining_low_mm:Option<i64>,pub remaining_high_mm:Option<i64>,pub remaining_days:Option<i64>,
    pub source_url:String,pub source_row:String,pub explanation:String,
    pub reminder_snoozed_until:Option<Date>,
}
pub fn calculate(rule:&Rule,service:Option<&Service>,current:Option<u64>,estimated:bool,today:&Date,severe:bool,
                 override_rule:Option<&Override>,source_url:&str,soon_mm:u64,soon_days:i64)->Due {
    let mut due=Due{item_id:rule.id.clone(),title:rule.title.clone(),action:rule.action.clone(),status:"unknownHistory".into(),uncertain:estimated,
        last_service_id:service.map(|s|s.id.clone()),last_date:service.and_then(|s|s.date.clone()),last_mileage:service.and_then(|s|s.mileage.clone()),
        next_low_mm:None,next_high_mm:None,next_date:None,remaining_low_mm:None,remaining_high_mm:None,remaining_days:None,
        source_url:source_url.into(),source_row:rule.source_row.clone(),explanation:String::new(),reminder_snoozed_until:None};
    if override_rule.is_some_and(|v|v.disabled){due.status="notApplicable".into();due.explanation="Disabled by the vehicle owner.".into();return due;}
    let Some(service)=service else{due.explanation="No service history was recorded. No completion or overdue status is assumed.".into();return due;};
    let interval=if let Some(custom)=override_rule {&custom.interval}
        else if severe {match rule.severe.as_ref(){Some(v)=>v,None=>{due.uncertain=true;due.explanation="Choose specific driving conditions in the vehicle profile to apply the verified item-specific notes, or set an explicit owner interval. A broad severe flag cannot select the correct exception.".into();return due;}}}
        else if service.factory_baseline {rule.initial.as_ref().unwrap_or(&rule.normal)}else{&rule.normal};
    if let (Some(miles),Some(range))=(interval.miles,&service.mileage) {
        let increment=miles.saturating_mul(MM_PER_MILE);
        due.next_low_mm=range.low_mm.checked_add(increment);due.next_high_mm=range.high_mm.checked_add(increment);
        due.uncertain|=range.low_mm!=range.high_mm;
        if let Some(now)=current {due.remaining_low_mm=due.next_low_mm.map(|v|v as i64-now as i64);due.remaining_high_mm=due.next_high_mm.map(|v|v as i64-now as i64);}
    }
    if let (Some(months),Some(last))=(interval.months,&service.date){due.next_date=last.add_months(months).ok();due.remaining_days=due.next_date.as_ref().map(|v|v.ordinal()-today.ordinal());}
    let values:[Option<i64>;2]=[due.remaining_high_mm,due.remaining_days];
    let overdue=values.iter().flatten().any(|v|*v<0);
    let is_due=values.iter().flatten().any(|v|*v==0);
    let ambiguous=matches!((due.remaining_low_mm,due.remaining_high_mm),(Some(low),Some(high)) if low<=0 && high>0);
    let soon=due.remaining_low_mm.is_some_and(|v|v>=0 && v<=soon_mm as i64) || due.remaining_days.is_some_and(|v|v>=0 && v<=soon_days);
    let missing=(interval.miles.is_some() && due.remaining_high_mm.is_none()) || (interval.months.is_some() && due.remaining_days.is_none());
    due.uncertain|=missing || ambiguous;
    due.status=if overdue{"overdue"}else if is_due{"due"}else if ambiguous || missing{"unknownHistory"}else if soon{if rule.action=="inspect"{"inspectionRecommended"}else{"dueSoon"}}else{"upToDate"}.into();
    due.explanation=format!("{}; mileage and calendar thresholds use whichever occurs first.{}{}",
        if override_rule.is_some(){"Owner interval"}else{"Manufacturer schedule"},
        if due.uncertain{" Estimated mileage, incomplete dates or approximate service history are explicitly retained."}else{""},
        if ambiguous{" The mileage range straddles the due threshold; a precise due state cannot be established."}else{""});
    due
}

/// Only apply an exception when its actual item-specific driving conditions match.
pub fn for_conditions(rule:&Rule,conditions:&[String])->(Rule,String){
    let mut selected=rule.clone();let mut notes=Vec::new();
    if let Some(exception)=&rule.exception {
        if exception.conditions.iter().any(|v|conditions.contains(v)){
            selected.normal=exception.interval.clone();selected.initial=None;selected.action=exception.action.clone();
            selected.source_row=format!("{}; page 31 {}",rule.source_row,exception.source);
            notes.push(format!("Verified condition-specific service: {}.",exception.source));
        }
    }
    for advisory in &rule.advisories {
        if advisory.conditions.is_empty() || advisory.conditions.iter().any(|v|conditions.contains(v)){notes.push(advisory.note.clone());}
    }
    (selected,notes.join(" "))
}
