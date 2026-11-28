//! Explicit, revision-checked backup review. Never rewrites an odometer audit record.
use super::{model::*,date::Date,validate};
use serde::{Deserialize,Serialize};
use serde_json::{Value,json};
use std::collections::{BTreeMap,BTreeSet};

#[derive(Clone,Debug,Deserialize,Serialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct Resolution { pub key:String, pub use_incoming:bool }

const COLLECTIONS:[&str;10]=["vehicles","readings","services","fuel","expenses","trips","customItems","overrides","snoozes","preferences"];
fn key(collection:&str,v:&Value)->String {
    if collection=="preferences" {return "preferences".into();}
    if collection=="overrides" || collection=="snoozes" {return format!("{}/{}:{}",collection,v["vehicleId"].as_str().unwrap_or(""),v["itemId"].as_str().unwrap_or(""));}
    format!("{}/{}",collection,v["id"].as_str().unwrap_or(""))
}
fn entries(doc:&Value,collection:&str)->Vec<Value> {
    if collection=="preferences" {vec![doc[collection].clone()]}else{doc[collection].as_array().cloned().unwrap_or_default()}
}
pub fn preview(local:&Garage,incoming:&Garage,today:&Date,now:u64)->Result<Value,String>{
    validate(incoming,today,now)?;
    let a=serde_json::to_value(local).map_err(|_|"Cannot review local garage")?;
    let b=serde_json::to_value(incoming).map_err(|_|"Cannot review backup")?;
    let mut added=0;let mut matching=0;let mut conflicts=Vec::new();
    for collection in COLLECTIONS {
        let existing:BTreeMap<_,_>=entries(&a,collection).into_iter().map(|v|(key(collection,&v),v)).collect();
        for v in entries(&b,collection){
            let k=key(collection,&v);
            if collection=="preferences" && local.vehicles.is_empty(){continue;}
            match existing.get(&k){
                None=>added+=1,Some(old) if old==&v=>matching+=1,
                Some(old)=>{
                    // Record reassignment and currency relabeling would reinterpret existing history.
                    let can_replace=collection!="readings" && (old["vehicleId"]==v["vehicleId"])
                        && !(collection=="vehicles" && old["currency"]!=v["currency"] &&
                            (local.services.iter().any(|s|s.vehicle_id==v["id"].as_str().unwrap_or("")) || local.fuel.iter().any(|s|s.vehicle_id==v["id"].as_str().unwrap_or("")) || local.expenses.iter().any(|s|s.vehicle_id==v["id"].as_str().unwrap_or(""))));
                    conflicts.push(json!({"key":k,"collection":collection,"local":old,"incoming":v,"canReplace":can_replace,
                        "reason":if collection=="readings"{"Odometer history is append-only. Keep this reading and use an explained correction in Garage if needed."}else if !can_replace{"Existing history cannot be assigned to another vehicle or currency."}else{"Choose which saved version to keep."}}));
                }
            }
        }
    }
    Ok(json!({"revision":local.revision,"newRecords":added,"matchingRecords":matching,"conflicts":conflicts}))
}
pub fn apply(local:&Garage,incoming:Garage,today:&Date,now:u64,revision:u64,resolutions:Vec<Resolution>)->Result<Garage,String>{
    if local.revision!=revision{return Err("Your garage changed after review. Review this backup again.".into());}
    let review=preview(local,&incoming,today,now)?;
    let mut choices=BTreeMap::new();
    for r in resolutions {if choices.insert(r.key,r.use_incoming).is_some(){return Err("Duplicate backup resolution".into());}}
    let conflicts=review["conflicts"].as_array().ok_or("Invalid backup review")?;
    let expected:BTreeSet<_>=conflicts.iter().map(|v|v["key"].as_str().unwrap().to_owned()).collect();
    if choices.keys().cloned().collect::<BTreeSet<_>>()!=expected{return Err("Review every backup conflict before importing.".into());}
    for c in conflicts {if choices[c["key"].as_str().unwrap()] && !c["canReplace"].as_bool().unwrap_or(false){return Err("This historical record must be kept. Add an audited correction instead.".into());}}
    let mut doc=serde_json::to_value(local).map_err(|_|"Cannot prepare import")?;
    let incoming=serde_json::to_value(incoming).map_err(|_|"Cannot prepare backup")?;
    for collection in COLLECTIONS {
        if collection=="preferences" {
            if local.vehicles.is_empty() || choices.get("preferences")==Some(&true){doc[collection]=incoming[collection].clone();}
            continue;
        }
        let records=doc[collection].as_array_mut().ok_or("Invalid local collection")?;
        let index:BTreeMap<_,_>=records.iter().enumerate().map(|(i,v)|(key(collection,v),i)).collect();
        for value in entries(&incoming,collection){
            let k=key(collection,&value);
            if let Some(i)=index.get(&k){if choices.get(&k)==Some(&true){records[*i]=value;}}
            else{records.push(value);}
        }
    }
    let merged:Garage=serde_json::from_value(doc).map_err(|_|"Unsupported imported garage")?;
    validate(&merged,today,now)?;Ok(merged)
}
