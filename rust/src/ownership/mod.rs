//! Local-first ownership model. No sockets, credentials, file access or vehicle commands.
pub mod date;
pub mod model;
pub mod maintenance;
pub mod analytics;
use date::Date;
use model::*;
use serde::{Deserialize, Serialize};
use serde_json::{json,Value};
use std::collections::{BTreeSet,BTreeMap};

pub const MAX_JSON:usize=4*1024*1024;
pub fn schedule()->Schedule {serde_json::from_str(include_str!("../../../vehicle-database/subaru-us-2017-legacy-25-cvt.json")).expect("Reviewed bundled maintenance data must parse")}

#[derive(Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Request {
    pub state:Garage,pub today:Date,pub now_ms:u64,pub command:Command,
    #[serde(default)]pub due_soon_mm:Option<u64>,
    #[serde(default)]pub due_soon_days:Option<i64>,
}
#[derive(Deserialize)]
#[serde(tag="type",rename_all="camelCase",rename_all_fields="camelCase",deny_unknown_fields)]
pub enum Command {
    View,AddVehicle{vehicle:Vehicle},UpdateVehicle{vehicle:Vehicle},AddReading{reading:Reading},
    AddService{service:Service},AddFuel{entry:Fuel},AddExpense{expense:Expense},AddTrip{trip:Trip},
    SetOverride{value:Override},ResetOverride{vehicle_id:String,item_id:String},MergeBackup{backup:Garage},
    UpdateService{service:Service},UpdateFuel{entry:Fuel},UpdateExpense{expense:Expense},UpdateTrip{trip:Trip},
    RecordFillUp{entry:Fuel,reading:Reading},
    AddCustomItem{item:CustomItem},UpdateCustomItem{item:CustomItem},SetPreferences{preferences:MaintenancePreferences},
    SnoozeReminder{value:ReminderSnooze},ClearSnooze{vehicle_id:String,item_id:String},
    Analyze{periods:Vec<analytics::Period>},
}
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub struct Mileage {
    pub current_mm:Option<u64>,pub estimated:bool,pub anchor_id:Option<String>,pub last_confirmed_mm:Option<u64>,
    pub added_trip_mm:u64,pub excluded_partial_trips:u32,pub average_mm_per_day:Option<u64>,pub warnings:Vec<String>,
}
pub fn mileage(state:&Garage,id:&str)->Mileage {
    let replaced:BTreeSet<_>=state.readings.iter().filter_map(|r|r.supersedes.as_deref()).collect();
    let mut readings:Vec<_>=state.readings.iter().filter(|r|r.vehicle_id==id && !replaced.contains(r.id.as_str())).collect();
    readings.sort_by_key(|r|(r.at_ms,&r.id));
    let confirmed:Vec<_>=readings.iter().copied().filter(|r|r.confidence==Confidence::Confirmed).collect();
    let mut result=Mileage{current_mm:None,estimated:false,anchor_id:None,last_confirmed_mm:confirmed.last().map(|r|r.distance_mm),added_trip_mm:0,excluded_partial_trips:0,average_mm_per_day:None,warnings:vec![]};
    let Some(mut anchor)=readings.last().copied() else {return result;};
    if let Some(last)=confirmed.last() {
        if anchor.distance_mm<last.distance_mm {
            result.warnings.push("An approximate reading falls below the confirmed odometer and was not used as the anchor. Review or correct the record.".into());
            anchor=last;
        }
    }
    result.anchor_id=Some(anchor.id.clone());result.estimated=anchor.confidence!=Confidence::Confirmed;
    for trip in state.trips.iter().filter(|t|t.vehicle_id==id && t.ended_ms>anchor.at_ms) {
        if trip.started_ms<anchor.at_ms{result.excluded_partial_trips+=1;continue;}
        result.added_trip_mm=result.added_trip_mm.saturating_add(trip.distance_mm);
    }
    result.current_mm=anchor.distance_mm.checked_add(result.added_trip_mm).filter(|v|*v<=MAX_DISTANCE);
    if result.current_mm.is_none(){result.warnings.push("The trip-based estimate exceeds the supported mileage range. Review the records.".into());}
    result.estimated|=result.added_trip_mm>0;
    if let (Some(first),Some(last))=(confirmed.first(),confirmed.last()) {
        let elapsed=last.at_ms-first.at_ms;
        if elapsed>=86_400_000 && last.distance_mm>=first.distance_mm {
            result.average_mm_per_day=Some(((last.distance_mm-first.distance_mm) as u128*86_400_000/elapsed as u128) as u64);
            if result.average_mm_per_day.is_some_and(|v|v>1000*MM_PER_MILE){result.warnings.push("Recorded growth exceeds 1,000 miles/day; review the readings. This is a review threshold, not a diagnosis.".into());}
        }
    }
    if result.excluded_partial_trips>0{result.warnings.push("A trip crossed the latest reading timestamp and was not added twice or split without evidence.".into());}
    result
}
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub struct FuelSummary {pub completed_cycles:u32,pub distance_mm:u64,pub volume_ml:u64,pub mpg_us:Option<f64>,pub litres_per_100_km:Option<f64>}
pub fn fuel_summary(state:&Garage,id:&str)->FuelSummary {
    fuel_entries(state.fuel.iter().filter(|e|e.vehicle_id==id).collect())
}
pub fn fuel_entries(mut entries:Vec<&Fuel>)->FuelSummary {
    entries.sort_by_key(|e|(e.at_ms,&e.id));
    let mut out=FuelSummary{completed_cycles:0,distance_mm:0,volume_ml:0,mpg_us:None,litres_per_100_km:None};
    let mut anchor:Option<u64>=None;let mut volume=0;
    for e in entries {
        if e.missed_fill {anchor=None;volume=0;}
        if anchor.is_some(){volume+=e.volume_ml;}
        if e.full {
            if let Some(start)=anchor {if e.distance_mm>=start && volume>0 {out.completed_cycles+=1;out.distance_mm+=e.distance_mm-start;out.volume_ml+=volume;}}
            anchor=Some(e.distance_mm);volume=0;
        }
    }
    if out.volume_ml>0 {
        out.mpg_us=Some((out.distance_mm as f64/MM_PER_MILE as f64)/(out.volume_ml as f64/3785.411784));
        if out.distance_mm>0{out.litres_per_100_km=Some(out.volume_ml as f64/1000.0/(out.distance_mm as f64/1_000_000.0)*100.0);}
    }out
}
/// A partial record must not sort before every dated record merely because its
/// date is unknown. Only choose a service that demonstrably follows all others.
pub fn latest_service<'a>(history:&[&'a Service])->Option<&'a Service>{
    fn after(a:&Service,b:&Service)->bool {
        if a.date==b.date && a.mileage==b.mileage && a.factory_baseline==b.factory_baseline{return true;}
        let date=match(&a.date,&b.date){(Some(x),Some(y))=>Some(x.cmp(y)),_=>None};
        let distance=match(&a.mileage,&b.mileage){
            (Some(x),Some(y)) if x.low_mm>y.high_mm=>Some(std::cmp::Ordering::Greater),
            (Some(x),Some(y)) if x.high_mm<y.low_mm=>Some(std::cmp::Ordering::Less),
            (Some(x),Some(y)) if x==y=>Some(std::cmp::Ordering::Equal),_=>None};
        if date==Some(std::cmp::Ordering::Less) || distance==Some(std::cmp::Ordering::Less){return false;}
        date==Some(std::cmp::Ordering::Greater) || distance==Some(std::cmp::Ordering::Greater)
    }
    // Any dominating item is a maximum by date or by a mileage bound. Test only
    // those candidates so contradictory imports cannot trigger a quadratic scan.
    let candidates=[
        history.iter().copied().max_by_key(|s|(s.date.as_ref(),s.mileage.as_ref().map(|r|r.low_mm))),
        history.iter().copied().max_by_key(|s|s.mileage.as_ref().map(|r|r.low_mm)),
        history.iter().copied().max_by_key(|s|s.mileage.as_ref().map(|r|r.high_mm)),
    ];
    candidates.into_iter().flatten().find(|candidate|history.iter().all(|other|after(candidate,other)))
}
fn id(value:&str)->Result<(),String>{if value.is_empty() || value.len()>80 || !value.bytes().all(|b|b.is_ascii_alphanumeric() || b"-_".contains(&b)){Err("Invalid record identifier".into())}else{Ok(())}}
fn text(value:&str,max:usize)->Result<(),String>{if value.len()>max || value.chars().any(|c|c=='\0'){Err("Text exceeds its limit or contains NUL".into())}else{Ok(())}}
fn range(value:&DistanceRange)->Result<(),String>{if value.low_mm>value.high_mm || value.high_mm>MAX_DISTANCE{Err("Invalid mileage range".into())}else{Ok(())}}
fn interval(value:&Interval)->Result<(),String>{if value.miles.is_none() && value.months.is_none() || value.miles.is_some_and(|v|v==0 || v>1_000_000) || value.months.is_some_and(|v|v==0 || v>2400){Err("Invalid service interval".into())}else{Ok(())}}
pub fn validate(state:&Garage,today:&Date,now:u64)->Result<(),String>{
    if state.schema_version!=1{return Err("Unsupported garage schema; data was not changed".into());}
    if state.vehicles.len()>50 || state.readings.len()+state.services.len()+state.fuel.len()+state.expenses.len()+state.trips.len()>MAX_RECORDS || state.overrides.len()>1000 || state.custom_items.len()>1000 || state.snoozes.len()>1000{return Err("Garage capacity exceeded".into());}
    if state.preferences.due_soon_mm>100_000*MM_PER_MILE || !(0..=3650).contains(&state.preferences.due_soon_days){return Err("Invalid maintenance reminder thresholds".into());}
    let mut identifiers=BTreeSet::new();let mut vehicles=BTreeSet::new();
    for vehicle in &state.vehicles {
        id(&vehicle.id)?;if !identifiers.insert(&vehicle.id){return Err("Duplicate vehicle identifier".into());}vehicles.insert(vehicle.id.as_str());
        if !(1886..=2200).contains(&vehicle.year) || vehicle.nickname.trim().is_empty() || vehicle.make.trim().is_empty() || vehicle.model.trim().is_empty(){return Err("Vehicle name, make, model and valid year are required".into());}
        for value in [&vehicle.nickname,&vehicle.make,&vehicle.model,&vehicle.trim,&vehicle.engine,&vehicle.transmission,&vehicle.drivetrain]{text(value,160)?;}
        text(&vehicle.notes,2000)?;
        if vehicle.currency.len()!=3 || !vehicle.currency.bytes().all(|v|v.is_ascii_uppercase()) || !["us","metric"].contains(&vehicle.units.as_str()){return Err("Choose supported units and a three-letter currency".into());}
        if let Some(vin)=&vehicle.vin {if !vin.is_empty() && (vin.len()!=17 || !vin.bytes().all(|v|v.is_ascii_uppercase() && !b"IOQ".contains(&v) || v.is_ascii_digit())){return Err("VIN must contain 17 valid uppercase characters".into());}}
        if let Some(plate)=&vehicle.plate{text(plate,32)?;}
        if vehicle.purchase_mileage_mm.is_some_and(|v|v>MAX_DISTANCE) || vehicle.purchase_price_cents.is_some_and(|v|v>1_000_000_000_000){return Err("Invalid purchase amount".into());}
        if vehicle.purchase_date.as_ref().is_some_and(|d|d>today) || vehicle.in_service_date.as_ref().is_some_and(|d|d>today){return Err("Historical vehicle dates cannot be in the future".into());}
        if let Some(profile)=&vehicle.maintenance_profile {
            let engine=vehicle.engine.split_whitespace().collect::<String>().to_ascii_lowercase();
            if profile!="subaru-us-2017-legacy-25-cvt" || vehicle.year!=2017 || !vehicle.make.eq_ignore_ascii_case("Subaru") || !vehicle.model.eq_ignore_ascii_case("Legacy") || !matches!(engine.as_str(),"2.5"|"2.5l"|"2.5lh4"|"fb25") || !vehicle.transmission.eq_ignore_ascii_case("CVT"){return Err("The bundled US schedule applies only to the selected 2017 Legacy 2.5 CVT profile".into());}
        }
    }
    let check_record=|key:&str,vehicle:&str,ids:&mut BTreeSet<String>|->Result<(),String>{id(key)?;if !ids.insert(key.into()){return Err("Duplicate record identifier".into());}if !vehicles.contains(vehicle){return Err("Record refers to an unknown vehicle".into());}Ok(())};
    let mut ids=BTreeSet::new();
    let by_id:BTreeMap<_,_>=state.readings.iter().map(|r|(r.id.as_str(),r)).collect();
    let mut replaced=BTreeSet::new();
    for reading in &state.readings {
        check_record(&reading.id,&reading.vehicle_id,&mut ids)?;text(&reading.notes,2000)?;
        if reading.distance_mm>MAX_DISTANCE || reading.at_ms>now{return Err("Invalid or future odometer reading".into());}
        if reading.source==DistanceSource::Gps && reading.confidence==Confidence::Confirmed{return Err("GPS mileage must be labeled approximate".into());}
        if let Some(old)=&reading.supersedes {
            let original=by_id.get(old.as_str()).ok_or("Correction refers to a missing reading")?;
            if old==&reading.id || original.vehicle_id!=reading.vehicle_id || reading.notes.trim().is_empty(){return Err("A correction needs the same vehicle and an explanation".into());}
            if !replaced.insert(old.as_str()){return Err("Reading already has a correction".into());}
        }
    }
    // Linear graph traversal avoids repeatedly walking every correction chain.
    let mut colors=BTreeMap::new();
    for reading in &state.readings {
        let mut path=Vec::new();let mut next=Some(reading.id.as_str());
        while let Some(key)=next {
            match colors.get(key){Some(1)=>return Err("Correction cycle rejected".into()),Some(2)=>break,_=>()}
            colors.insert(key,1);path.push(key);next=by_id.get(key).and_then(|r|r.supersedes.as_deref());
        }
        for key in path{colors.insert(key,2);}
    }
    for vehicle in &state.vehicles {
        let mut times=BTreeSet::new();for reading in state.readings.iter().filter(|r|r.vehicle_id==vehicle.id && !replaced.contains(r.id.as_str())) {
            if !times.insert(reading.at_ms){return Err("Duplicate active odometer reading timestamp".into());}
        }
        let mut readings:Vec<_>=state.readings.iter().filter(|r|r.vehicle_id==vehicle.id && !replaced.contains(r.id.as_str()) && r.confidence==Confidence::Confirmed).collect();readings.sort_by_key(|r|r.at_ms);
        for pair in readings.windows(2) {
            if pair[0].at_ms==pair[1].at_ms{return Err("Duplicate confirmed reading timestamp".into());}
            if pair[1].distance_mm<pair[0].distance_mm{return Err("Active confirmed readings decrease; correct the erroneous measurement rather than creating a new lower odometer".into());}
        }
    }
    let rules=schedule();let mut custom_ids=BTreeSet::new();
    for item in &state.custom_items {
        id(&item.id)?;text(&item.title,160)?;text(&item.notes,2000)?;interval(&item.interval)?;
        if !vehicles.contains(item.vehicle_id.as_str()) || !custom_ids.insert(item.id.as_str()) || rules.rules.iter().any(|r|r.id==item.id) || item.title.trim().is_empty() || !["inspect","replace","renew","service"].contains(&item.action.as_str()){return Err("Invalid or duplicate owner maintenance item".into());}
    }
    let valid_rule=|vehicle:&str,key:&str|rules.rules.iter().any(|r|r.id==key) || state.custom_items.iter().any(|r|r.id==key && r.vehicle_id==vehicle);
    for service in &state.services {
        check_record(&service.id,&service.vehicle_id,&mut ids)?;text(&service.provider,160)?;text(&service.notes,2000)?;text(&service.warranty,2000)?;
        if service.part_numbers.len()>50{return Err("Too many service part numbers".into());}for value in &service.part_numbers{text(value,160)?;}
        if service.date.is_none() && service.mileage.is_none(){return Err("Record a service date or mileage range".into());}
        if service.date.as_ref().is_some_and(|d|d>today) || service.item_ids.is_empty() || service.item_ids.len()>50 || service.item_ids.iter().any(|v|!valid_rule(&service.vehicle_id,v)){return Err("Invalid service date or maintenance item".into());}
        let distinct:BTreeSet<_>=service.item_ids.iter().collect();if distinct.len()!=service.item_ids.len(){return Err("Duplicate service item".into());}
        if service.cost_cents>1_000_000_000{return Err("Service amount exceeds supported limit".into());}
        if let Some(cost)=&service.cost_breakdown {
            let sum=cost.parts_cents.checked_add(cost.labor_cents).and_then(|v|v.checked_add(cost.tax_cents));
            if sum!=Some(service.cost_cents){return Err("Parts, labor and tax must add up to the service total".into());}
        }
        if service.factory_baseline && (service.cost_cents!=0 || service.mileage.as_ref().is_some_and(|v|v.low_mm!=0 || v.high_mm!=0)) {
            return Err("A factory baseline must have zero mileage and no paid service cost".into());
        }
        if let Some(value)=&service.mileage{range(value)?;}
    }
    for fuel in &state.fuel {
        check_record(&fuel.id,&fuel.vehicle_id,&mut ids)?;text(&fuel.notes,2000)?;
        if fuel.at_ms>now || fuel.distance_mm>MAX_DISTANCE || fuel.volume_ml==0 || fuel.volume_ml>1_000_000 || fuel.cost_cents>100_000_000{return Err("Invalid fuel entry".into());}
    }
    for vehicle in &state.vehicles {
        let mut fuel:Vec<_>=state.fuel.iter().filter(|f|f.vehicle_id==vehicle.id).collect();fuel.sort_by_key(|f|f.at_ms);
        for pair in fuel.windows(2){if pair[1].at_ms==pair[0].at_ms || pair[1].distance_mm<pair[0].distance_mm{return Err("Fuel chronology has a duplicate timestamp or decreasing odometer".into());}}
    }
    for expense in &state.expenses {
        check_record(&expense.id,&expense.vehicle_id,&mut ids)?;text(&expense.category,80)?;text(&expense.notes,2000)?;
        if expense.category.trim().is_empty() || expense.date> *today || expense.cost_cents>1_000_000_000{return Err("Invalid expense record".into());}
    }
    for trip in &state.trips {
        check_record(&trip.id,&trip.vehicle_id,&mut ids)?;text(&trip.notes,2000)?;
        if trip.started_ms>=trip.ended_ms || trip.ended_ms>now || trip.distance_mm>10_000*MM_PER_MILE{return Err("Invalid trip bounds".into());}
    }
    for vehicle in &state.vehicles {
        let mut trips:Vec<_>=state.trips.iter().filter(|t|t.vehicle_id==vehicle.id).collect();trips.sort_by_key(|t|t.started_ms);
        for pair in trips.windows(2){if pair[1].started_ms<pair[0].ended_ms{return Err("Overlapping trips would double-count mileage".into());}}
    }
    let mut overrides=BTreeSet::new();for custom in &state.overrides {if !vehicles.contains(custom.vehicle_id.as_str()) || !valid_rule(&custom.vehicle_id,&custom.item_id) || !overrides.insert((&custom.vehicle_id,&custom.item_id)){return Err("Invalid or duplicate maintenance override".into());}interval(&custom.interval)?;}
    let mut snoozes=BTreeSet::new();for value in &state.snoozes {if !vehicles.contains(value.vehicle_id.as_str()) || !valid_rule(&value.vehicle_id,&value.item_id) || !snoozes.insert((&value.vehicle_id,&value.item_id)){return Err("Invalid or duplicate maintenance reminder snooze".into());}}
    Ok(())
}
fn merge<T:Clone+PartialEq>(current:&mut Vec<T>,incoming:Vec<T>,key:impl Fn(&T)->&str)->Result<(),String>{
    for value in incoming {if let Some(old)=current.iter().find(|v|key(v)==key(&value)){if old!=&value{return Err("Backup contains conflicting records; nothing was imported".into());}}else{current.push(value);}}Ok(())
}
fn replace<T>(records:&mut [T],value:T,key:impl Fn(&T)->(&str,&str))->Result<(),String>{
    let record=records.iter_mut().find(|r|key(r).0==key(&value).0).ok_or("Record not found")?;
    if key(record).1!=key(&value).1{return Err("A record cannot be moved to another vehicle".into());}
    *record=value;Ok(())
}
pub fn execute(request:Request)->Result<Value,String>{
    let mut state=request.state;validate(&state,&request.today,request.now_ms)?;
    if request.due_soon_mm.is_some_and(|v|v>100_000*MM_PER_MILE) || request.due_soon_days.is_some_and(|v|!(0..=3650).contains(&v)){return Err("Invalid due-soon thresholds".into());}
    let original=state.clone();
    let mut periods=Vec::new();
    match request.command {
        Command::View=>(),Command::AddVehicle{vehicle}=>state.vehicles.push(vehicle),
        Command::Analyze{periods:requested}=>{
            if requested.is_empty() || requested.len()>24{return Err("Choose one to 24 analytics periods".into());}
            let mut ids=BTreeSet::new();for period in &requested{period.validate()?;if !ids.insert(&period.id){return Err("Duplicate analytics period identifier".into());}}
            periods=requested;
        },
        Command::UpdateVehicle{vehicle}=>{
            let old=state.vehicles.iter_mut().find(|v|v.id==vehicle.id).ok_or("Vehicle not found")?;
            let recorded_costs=state.services.iter().any(|v|v.vehicle_id==vehicle.id) || state.fuel.iter().any(|v|v.vehicle_id==vehicle.id) || state.expenses.iter().any(|v|v.vehicle_id==vehicle.id);
            if old.currency!=vehicle.currency && recorded_costs{return Err("Currency cannot change after cost records are saved; recorded amounts are never silently converted".into());}
            *old=vehicle;
        },
        Command::AddReading{reading}=>state.readings.push(reading),Command::AddService{service}=>state.services.push(service),
        Command::AddFuel{entry}=>state.fuel.push(entry),Command::AddExpense{expense}=>state.expenses.push(expense),Command::AddTrip{trip}=>state.trips.push(trip),
        Command::RecordFillUp{entry,reading}=>{
            if entry.vehicle_id!=reading.vehicle_id || entry.at_ms!=reading.at_ms || entry.distance_mm!=reading.distance_mm || reading.confidence!=Confidence::Confirmed || reading.source!=DistanceSource::Manual || reading.supersedes.is_some(){return Err("A fill-up odometer must be a matching confirmed manual reading".into());}
            state.fuel.push(entry);state.readings.push(reading);
        },
        Command::UpdateService{service}=>replace(&mut state.services,service,|v|(&v.id,&v.vehicle_id))?,
        Command::UpdateFuel{entry}=>replace(&mut state.fuel,entry,|v|(&v.id,&v.vehicle_id))?,
        Command::UpdateExpense{expense}=>replace(&mut state.expenses,expense,|v|(&v.id,&v.vehicle_id))?,
        Command::UpdateTrip{trip}=>replace(&mut state.trips,trip,|v|(&v.id,&v.vehicle_id))?,
        Command::SetOverride{value}=>{if let Some(old)=state.overrides.iter_mut().find(|v|v.vehicle_id==value.vehicle_id && v.item_id==value.item_id){*old=value;}else{state.overrides.push(value);}},
        Command::ResetOverride{vehicle_id,item_id}=>{state.overrides.retain(|v|v.vehicle_id!=vehicle_id || v.item_id!=item_id);},
        Command::AddCustomItem{item}=>state.custom_items.push(item),
        Command::UpdateCustomItem{item}=>replace(&mut state.custom_items,item,|v|(&v.id,&v.vehicle_id))?,
        Command::SetPreferences{preferences}=>state.preferences=preferences,
        Command::SnoozeReminder{value}=>{if let Some(old)=state.snoozes.iter_mut().find(|v|v.vehicle_id==value.vehicle_id && v.item_id==value.item_id){*old=value;}else{state.snoozes.push(value);}},
        Command::ClearSnooze{vehicle_id,item_id}=>state.snoozes.retain(|v|v.vehicle_id!=vehicle_id || v.item_id!=item_id),
        Command::MergeBackup{backup}=>{
            validate(&backup,&request.today,request.now_ms)?;
            if state.vehicles.is_empty(){state.preferences=backup.preferences.clone();}else if state.preferences!=backup.preferences{return Err("Backup reminder settings conflict with local settings; nothing was imported".into());}
            merge(&mut state.custom_items,backup.custom_items,|v|&v.id)?;
            merge(&mut state.vehicles,backup.vehicles,|v|&v.id)?;merge(&mut state.readings,backup.readings,|v|&v.id)?;
            merge(&mut state.services,backup.services,|v|&v.id)?;merge(&mut state.fuel,backup.fuel,|v|&v.id)?;
            merge(&mut state.expenses,backup.expenses,|v|&v.id)?;merge(&mut state.trips,backup.trips,|v|&v.id)?;
            for value in backup.overrides {if let Some(old)=state.overrides.iter().find(|v|v.vehicle_id==value.vehicle_id && v.item_id==value.item_id){if old!=&value{return Err("Backup interval conflicts with local data".into());}}else{state.overrides.push(value);}}
            for value in backup.snoozes {if let Some(old)=state.snoozes.iter().find(|v|v.vehicle_id==value.vehicle_id && v.item_id==value.item_id){if old!=&value{return Err("Backup reminder snooze conflicts with local data".into());}}else{state.snoozes.push(value);}}
        }
    }
    validate(&state,&request.today,request.now_ms)?;
    let changed=state!=original;
    if changed{state.revision=state.revision.checked_add(1).ok_or("Revision exhausted")?;}
    let schedule=schedule();let mut summaries=Vec::new();
    let soon_mm=request.due_soon_mm.unwrap_or(state.preferences.due_soon_mm);let soon_days=request.due_soon_days.unwrap_or(state.preferences.due_soon_days);
    for vehicle in &state.vehicles {
        let miles=mileage(&state,&vehicle.id);
        let services:Vec<_>=state.services.iter().filter(|s|s.vehicle_id==vehicle.id).collect();
        let mut due=Vec::new();
        if vehicle.maintenance_profile.as_deref()==Some(schedule.profile.as_str()) {
            for rule in &schedule.rules {
                let history:Vec<_>=services.iter().copied().filter(|s|s.item_ids.contains(&rule.id)).collect();
                let last=latest_service(&history);
                let custom=state.overrides.iter().find(|v|v.vehicle_id==vehicle.id && v.item_id==rule.id);
                let mut status=maintenance::calculate(rule,last,miles.current_mm,miles.estimated,&request.today,vehicle.severe,custom,&schedule.source_url,soon_mm,soon_days);
                if !history.is_empty() && last.is_none() && status.status!="notApplicable" {
                    status.uncertain=true;status.explanation="Service records cannot be reliably ordered. Add the missing dates or mileage before choosing a last service.".into();
                }
                due.push(status);
            }
        }
        for item in state.custom_items.iter().filter(|v|v.vehicle_id==vehicle.id) {
            let rule=Rule{id:item.id.clone(),title:item.title.clone(),action:item.action.clone(),normal:item.interval.clone(),initial:None,severe:None,source_row:"Owner-defined item; no manufacturer interval is claimed".into(),note_refs:vec![]};
            let history:Vec<_>=services.iter().copied().filter(|s|s.item_ids.contains(&item.id)).collect();let last=latest_service(&history);
            let default=Override{vehicle_id:vehicle.id.clone(),item_id:item.id.clone(),interval:item.interval.clone(),disabled:false};
            let custom=state.overrides.iter().find(|v|v.vehicle_id==vehicle.id && v.item_id==item.id).unwrap_or(&default);
            let mut status=maintenance::calculate(&rule,last,miles.current_mm,miles.estimated,&request.today,false,Some(custom),"",soon_mm,soon_days);
            if !history.is_empty() && last.is_none() && status.status!="notApplicable"{status.uncertain=true;status.explanation="Owner-defined item. Service records cannot be reliably ordered; add the missing date or mileage.".into();}
            due.push(status);
        }
        for item in &mut due {item.reminder_snoozed_until=state.snoozes.iter().find(|v|v.vehicle_id==vehicle.id && v.item_id==item.item_id && v.until>=request.today).map(|v|v.until.clone());}
        let fuel_cost:u64=state.fuel.iter().filter(|e|e.vehicle_id==vehicle.id).map(|e|e.cost_cents).sum();
        let service_cost:u64=services.iter().filter(|s|!s.factory_baseline).map(|s|s.cost_cents).sum();
        let other:u64=state.expenses.iter().filter(|e|e.vehicle_id==vehicle.id && !e.financing).map(|e|e.cost_cents).sum();
        let financing:u64=state.expenses.iter().filter(|e|e.vehicle_id==vehicle.id && e.financing).map(|e|e.cost_cents).sum();
        summaries.push(json!({"vehicleId":vehicle.id,"mileage":miles,"maintenance":due,"fuel":fuel_summary(&state,&vehicle.id),
            "costs":{"currency":vehicle.currency,"fuelCents":fuel_cost,"serviceCents":service_cost,"otherOperatingCents":other,"financingCents":financing,"recordedOperatingCents":fuel_cost+service_cost+other,"recordedTotalCents":fuel_cost+service_cost+other+financing},
            "counts":{"readings":state.readings.iter().filter(|r|r.vehicle_id==vehicle.id).count(),"services":services.len(),"fuel":state.fuel.iter().filter(|e|e.vehicle_id==vehicle.id).count(),"expenses":state.expenses.iter().filter(|e|e.vehicle_id==vehicle.id).count(),"trips":state.trips.iter().filter(|e|e.vehicle_id==vehicle.id).count()},
            "analytics":periods.iter().map(|period|analytics::summarize(&state,&vehicle.id,period)).collect::<Vec<_>>() }));
    }
    Ok(json!({"ok":true,"changed":changed,"state":state,"summaries":summaries,"schedule":schedule,"capabilities":crate::capabilities::registry()}))
}
pub fn execute_json(input:&[u8])->Vec<u8>{
    let result=if input.len()>MAX_JSON{Err("Ownership request exceeds size limit".into())}else{serde_json::from_slice::<Request>(input).map_err(|_|"Invalid ownership request or unsupported fields".into()).and_then(execute)};
    serde_json::to_vec(&match result{Ok(value)=>value,Err(error)=>json!({"ok":false,"error":error})}).unwrap_or_else(|_|b"{\"ok\":false,\"error\":\"Serialization failed\"}".to_vec())
}
