use subarubridge_core::ownership::{self as own,date::Date,model::*,maintenance};
use serde_json::{json,Value};

#[test]fn imported_documents_are_strict_and_nesting_is_bounded_before_native_parsing(){
 assert!(own::valid_document(b"\xef\xbb\xbf{\"valid\":true}"));assert!(!own::valid_document(b"{'not':'json'}"));assert!(!own::valid_document(b"{} trailing"));
 let deep=format!("{}0{}","[".repeat(1000),"]".repeat(1000));assert!(!own::valid_document(deep.as_bytes()));
 let quoted=json!({"notes":"{".repeat(1000)}).to_string();assert!(own::valid_document(quoted.as_bytes()));
 assert!(!own::valid_document(br#"{"notes":"\uD800"}"#));assert!(!own::valid_document(&vec![b' ';own::MAX_JSON+1]));
}

#[test]fn version_counters_remain_exact_across_native_and_json_adapters(){
 let mut s=garage();s.revision=MAX_REVISION;assert!(valid(&s).is_ok());assert_eq!(execute(&s,json!({"type":"view"}))["ok"],true);
 let value=Expense{id:"e".into(),vehicle_id:"car".into(),date:date("2026-11-01"),category:"Parking".into(),cost_cents:100,financing:false,notes:String::new()};
 assert_eq!(execute(&s,json!({"type":"addExpense","expense":value}))["ok"],false);s.revision+=1;assert!(valid(&s).is_err());
}

#[test]fn service_cost_breakdown_uses_exact_minor_units_and_must_match_the_total(){
 let mut s=garage();let mut paid=service("s","engine-oil",Some((81000,81000)),Some("2026-11-01"));paid.cost_cents=12345;paid.cost_breakdown=Some(CostBreakdown{parts_cents:5000,labor_cents:7000,tax_cents:345});paid.diy=true;paid.part_numbers=vec!["Owner-recorded-part".into()];paid.warranty="12 months per owner receipt".into();s.services.push(paid);assert!(valid(&s).is_ok());assert_eq!(execute(&s,json!({"type":"view"}))["summaries"][0]["costs"]["serviceCents"],12345);
 s.services[0].cost_cents=12346;assert!(valid(&s).is_err());s.services[0].cost_breakdown=Some(CostBreakdown{parts_cents:u64::MAX,labor_cents:1,tax_cents:0});assert!(valid(&s).is_err());
}
#[test]fn earlier_service_records_load_with_empty_optional_service_book_details(){
 let value:Service=serde_json::from_value(json!({"id":"s","vehicleId":"car","itemIds":["engine-oil"],"date":"2026-11-01"})).unwrap();assert!(value.cost_breakdown.is_none());assert!(!value.diy);assert!(value.part_numbers.is_empty() && value.warranty.is_empty());
}

fn owner_item()->CustomItem{CustomItem{id:"registration".into(),vehicle_id:"car".into(),title:"Registration renewal".into(),action:"renew".into(),interval:Interval{miles:None,months:Some(12)},notes:"Owner-selected annual interval".into()}}
#[test]fn owner_maintenance_works_without_a_supported_manufacturer_profile(){
 let mut s=garage();s.vehicles[0].maintenance_profile=None;s.vehicles[0].severe=true;s.custom_items.push(owner_item());s.services.push(service("renewed","registration",None,Some("2025-11-27")));
 let out=execute(&s,json!({"type":"view"}));assert_eq!(out["ok"],true);let d=&out["summaries"][0]["maintenance"][0];assert_eq!(d["status"],"due");assert_eq!(d["sourceUrl"],"");assert!(d["explanation"].as_str().unwrap().contains("Owner interval"));
}
#[test]fn owner_items_reject_duplicates_invalid_actions_and_cross_vehicle_references(){
 let mut s=garage();s.custom_items.push(owner_item());assert!(valid(&s).is_ok());s.custom_items.push(owner_item());assert!(valid(&s).is_err());s.custom_items.pop();s.custom_items[0].action="manufacturer-approved".into();assert!(valid(&s).is_err());s.custom_items[0]=owner_item();
 let mut other=vehicle();other.id="other".into();s.vehicles.push(other);let mut wrong=service("s","registration",None,Some("2026-11-01"));wrong.vehicle_id="other".into();s.services.push(wrong);assert!(valid(&s).is_err());
}
#[test]fn owner_maintenance_edits_keep_saved_service_history(){
 let mut s=garage();s.custom_items.push(owner_item());s.services.push(service("s","registration",None,Some("2025-11-27")));let mut changed=owner_item();changed.interval.months=Some(24);let out=execute(&s,json!({"type":"updateCustomItem","item":changed}));assert_eq!(out["ok"],true);
 let status=out["summaries"][0]["maintenance"].as_array().unwrap().iter().find(|v|v["itemId"]=="registration").unwrap();assert_eq!(status["status"],"upToDate");assert_eq!(out["state"]["services"].as_array().unwrap().len(),1);
}
#[test]fn reminder_preferences_change_the_soon_window_without_changing_the_service_interval(){
 let mut s=garage();s.readings.push(reading("r",1,85000));s.services.push(service("s","engine-oil",Some((80000,80000)),Some("2026-10-01")));
 let status=|v:&Value|v["summaries"][0]["maintenance"].as_array().unwrap().iter().find(|v|v["itemId"]=="engine-oil").unwrap().clone();assert_eq!(status(&execute(&s,json!({"type":"view"})))["status"],"upToDate");
 let out=execute(&s,json!({"type":"setPreferences","preferences":{"dueSoonMm":2000*MM_PER_MILE,"dueSoonDays":30}}));let due=status(&out);assert_eq!(due["status"],"dueSoon");assert_eq!(due["nextLowMm"],86000*MM_PER_MILE);
 assert_eq!(execute(&s,json!({"type":"setPreferences","preferences":{"dueSoonMm":0,"dueSoonDays":-1}}))["ok"],false);
}
#[test]fn snooze_does_not_hide_the_maintenance_status_and_expires_by_local_civil_date(){
 let mut s=garage();s.custom_items.push(owner_item());s.services.push(service("s","registration",None,Some("2025-01-01")));
 let out=execute(&s,json!({"type":"snoozeReminder","value":{"vehicleId":"car","itemId":"registration","until":"2026-12-04"}}));assert_eq!(out["ok"],true);let next:Garage=serde_json::from_value(out["state"].clone()).unwrap();
 let status=|v:&Value|v["summaries"][0]["maintenance"].as_array().unwrap().iter().find(|v|v["itemId"]=="registration").unwrap().clone();let d=status(&out);assert_eq!(d["status"],"overdue");assert_eq!(d["reminderSnoozedUntil"],"2026-12-04");
 let cleared=execute(&next,json!({"type":"clearSnooze","vehicleId":"car","itemId":"registration"}));assert!(status(&cleared)["reminderSnoozedUntil"].is_null());
 s.snoozes.push(ReminderSnooze{vehicle_id:"car".into(),item_id:"registration".into(),until:date("2026-11-26")});assert!(status(&execute(&s,json!({"type":"view"})))["reminderSnoozedUntil"].is_null());
}
#[test]fn backups_include_custom_items_settings_and_snoozes(){
 let mut s=garage();s.custom_items.push(owner_item());s.preferences.due_soon_days=60;s.snoozes.push(ReminderSnooze{vehicle_id:"car".into(),item_id:"registration".into(),until:date("2026-12-01")});
 let out=execute(&Garage::empty(),json!({"type":"mergeBackup","backup":s}));assert_eq!(out["ok"],true);assert_eq!(out["state"]["customItems"].as_array().unwrap().len(),1);assert_eq!(out["state"]["preferences"]["dueSoonDays"],60);assert_eq!(out["state"]["snoozes"].as_array().unwrap().len(),1);
 assert_eq!(execute(&garage(),json!({"type":"mergeBackup","backup":s}))["ok"],false);
}

#[test]fn fuel_and_optional_odometer_are_validated_as_one_transaction(){
 let mut s=garage();s.readings.push(reading("anchor",1,80000));let f=fuel("f",2,81000,40000,true);let r=reading("fuel-reading",2,81000);
 let out=execute(&s,json!({"type":"recordFillUp","entry":f,"reading":r}));assert_eq!(out["ok"],true);assert_eq!(out["state"]["revision"],1);assert_eq!(out["state"]["fuel"].as_array().unwrap().len(),1);assert_eq!(out["summaries"][0]["mileage"]["currentMm"],81000*MM_PER_MILE);
 let mismatched=reading("fuel-reading",2,80999);assert_eq!(execute(&s,json!({"type":"recordFillUp","entry":f,"reading":mismatched}))["ok"],false);
 let decreasing=fuel("f",2,79000,40000,true);let low=reading("fuel-reading",2,79000);let bad=execute(&s,json!({"type":"recordFillUp","entry":decreasing,"reading":low}));assert_eq!(bad["ok"],false);assert!(bad.get("state").is_none());
}

#[test]fn record_edits_recalculate_without_adding_a_second_cost(){
 let mut s=garage();let mut old=service("paid","engine-oil",Some((70000,74000)),None);old.cost_cents=5000;s.services.push(old.clone());
 let mut fixed=old;fixed.date=Some(date("2026-10-01"));fixed.mileage=Some(DistanceRange{low_mm:80000*MM_PER_MILE,high_mm:80000*MM_PER_MILE});fixed.cost_cents=6000;
 s.readings.push(reading("r",100,81000));let out=execute(&s,json!({"type":"updateService","service":fixed}));assert_eq!(out["ok"],true);assert_eq!(out["state"]["services"].as_array().unwrap().len(),1);
 assert_eq!(out["summaries"][0]["costs"]["serviceCents"],6000);let due=out["summaries"][0]["maintenance"].as_array().unwrap().iter().find(|v|v["itemId"]=="engine-oil").unwrap();assert_eq!(due["status"],"upToDate");
}
#[test]fn record_edits_cannot_move_between_vehicles_or_create_missing_ids(){
 let mut s=garage();s.services.push(service("s","engine-oil",Some((80000,80000)),Some("2026-10-01")));let mut other=vehicle();other.id="second".into();s.vehicles.push(other);
 let mut changed=s.services[0].clone();changed.vehicle_id="second".into();assert_eq!(execute(&s,json!({"type":"updateService","service":changed}))["ok"],false);
 changed.vehicle_id="car".into();changed.id="missing".into();assert_eq!(execute(&s,json!({"type":"updateService","service":changed}))["ok"],false);
}
#[test]fn fuel_trip_and_expense_edits_preserve_validation(){
 let mut s=garage();s.fuel=vec![fuel("a",1,80000,40000,true),fuel("b",2,80300,40000,true)];let mut changed=s.fuel[1].clone();changed.distance_mm=79900*MM_PER_MILE;
 assert_eq!(execute(&s,json!({"type":"updateFuel","entry":changed}))["ok"],false);changed.distance_mm=80400*MM_PER_MILE;assert_eq!(execute(&s,json!({"type":"updateFuel","entry":changed}))["ok"],true);
 let trip=Trip{id:"t".into(),vehicle_id:"car".into(),started_ms:10,ended_ms:20,distance_mm:MM_PER_MILE,source:DistanceSource::Manual,notes:String::new()};s.trips.push(trip.clone());let mut invalid=trip;invalid.ended_ms=9;
 assert_eq!(execute(&s,json!({"type":"updateTrip","trip":invalid}))["ok"],false);
 let expense=Expense{id:"e".into(),vehicle_id:"car".into(),date:date("2026-11-01"),category:"Parking".into(),cost_cents:100,financing:false,notes:String::new()};s.expenses.push(expense.clone());let mut edited=expense;edited.cost_cents=200;
 assert_eq!(execute(&s,json!({"type":"updateExpense","expense":edited}))["summaries"][0]["costs"]["otherOperatingCents"],200);
}
#[test]fn currency_changes_never_relabel_existing_costs(){
 let mut s=garage();let mut changed=vehicle();changed.currency="CAD".into();assert_eq!(execute(&s,json!({"type":"updateVehicle","vehicle":changed}))["ok"],true);
 s.fuel.push(fuel("f",1,80000,40000,true));assert_eq!(execute(&s,json!({"type":"updateVehicle","vehicle":changed}))["ok"],false);
}
#[test]fn owner_intervals_can_be_restored_without_losing_service_records(){
 let mut s=garage();s.overrides.push(Override{vehicle_id:"car".into(),item_id:"engine-oil".into(),interval:Interval{miles:Some(4000),months:None},disabled:false});s.services.push(service("s","engine-oil",Some((80000,80000)),Some("2026-10-01")));
 let out=execute(&s,json!({"type":"resetOverride","vehicleId":"car","itemId":"engine-oil"}));assert_eq!(out["ok"],true);assert!(out["state"]["overrides"].as_array().unwrap().is_empty());assert_eq!(out["state"]["services"].as_array().unwrap().len(),1);
}
fn date(s:&str)->Date{Date::parse(s).unwrap()}
fn vehicle()->Vehicle{serde_json::from_value(json!({"id":"car","nickname":"Legacy","make":"Subaru","model":"Legacy","year":2017,"engine":"2.5L","transmission":"CVT","maintenanceProfile":"subaru-us-2017-legacy-25-cvt"})).unwrap()}
fn garage()->Garage{let mut s=Garage::empty();s.vehicles.push(vehicle());s}
fn reading(id:&str,at:u64,miles:u64)->Reading{Reading{id:id.into(),vehicle_id:"car".into(),at_ms:at,distance_mm:miles*MM_PER_MILE,source:DistanceSource::Manual,confidence:Confidence::Confirmed,supersedes:None,notes:String::new()}}
fn service(id:&str,item:&str,miles:Option<(u64,u64)>,when:Option<&str>)->Service{Service{id:id.into(),vehicle_id:"car".into(),item_ids:vec![item.into()],date:when.map(date),mileage:miles.map(|(lo,hi)|DistanceRange{low_mm:lo*MM_PER_MILE,high_mm:hi*MM_PER_MILE}),cost_cents:0,provider:String::new(),notes:String::new(),factory_baseline:false,cost_breakdown:None,diy:false,part_numbers:vec![],warranty:String::new()}}
fn due(item:&str,s:Option<&Service>,miles:Option<u64>,severe:bool,custom:Option<&Override>)->maintenance::Due{let db=own::schedule();let r=db.rules.iter().find(|r|r.id==item).unwrap();maintenance::calculate(r,s,miles.map(|v|v*MM_PER_MILE),false,&date("2026-11-27"),severe,custom,&db.source_url,500*MM_PER_MILE,30)}
fn execute(state:&Garage,command:Value)->Value{serde_json::from_slice(&own::execute_json(&serde_json::to_vec(&json!({"state":state,"today":"2026-11-27","nowMs":1800000000000u64,"command":command})).unwrap())).unwrap()}
fn valid(s:&Garage)->Result<(),String>{own::validate(s,&date("2026-11-27"),1800000000000)}
fn fuel(id:&str,at:u64,miles:u64,ml:u64,full:bool)->Fuel{Fuel{id:id.into(),vehicle_id:"car".into(),at_ms:at,distance_mm:miles*MM_PER_MILE,volume_ml:ml,cost_cents:1000,full,missed_fill:false,notes:String::new()}}

#[test]fn calendar_validation_and_month_ends(){
 for s in ["2026-02-29","1900-02-29","2026-13-01","2026-00-01","2026-11-31","2026-1-01","2026-01-01x"]{assert!(Date::parse(s).is_err(),"{s}");}
 assert_eq!(date("2024-01-31").add_months(1).unwrap().text(),"2024-02-29");
 assert_eq!(date("2025-01-31").add_months(1).unwrap().text(),"2025-02-28");
 assert_eq!(date("2000-02-29").add_months(12).unwrap().text(),"2001-02-28");
 assert_eq!(date("2026-03-01").ordinal()-date("2026-02-28").ordinal(),1);
 assert!(date("2200-12-01").add_months(1).is_err());
}
#[test]fn bundled_schedule_is_scoped_and_sourced(){let db=own::schedule();assert_eq!(db.rules.len(),18);assert!(db.source_url.starts_with("https://contactus.subaru.com/"));assert_eq!(db.source_sha256.len(),64);let oil=db.rules.iter().find(|r|r.id=="engine-oil").unwrap();assert_eq!(oil.normal,Interval{miles:Some(6000),months:Some(6)});assert!(db.rules.iter().all(|r|r.severe.is_none()));}
#[test]fn unknown_history_does_not_become_overdue(){let d=due("engine-oil",None,Some(81000),false,None);assert_eq!(d.status,"unknownHistory");assert!(d.next_date.is_none()&&d.next_low_mm.is_none());}
#[test]fn mileage_threshold_due_soon_due_and_overdue(){let s=service("s","engine-oil",Some((70000,70000)),Some("2026-09-01"));for (m,status) in [(75000,"upToDate"),(75500,"dueSoon"),(76000,"due"),(76001,"overdue")]{assert_eq!(due("engine-oil",Some(&s),Some(m),false,None).status,status);}}
#[test]fn calendar_threshold_wins_even_with_low_mileage(){let s=service("s","engine-oil",Some((70000,70000)),Some("2026-05-27"));assert_eq!(due("engine-oil",Some(&s),Some(71000),false,None).status,"due");let s=service("s","engine-oil",Some((70000,70000)),Some("2026-05-26"));assert_eq!(due("engine-oil",Some(&s),Some(71000),false,None).status,"overdue");}
#[test]fn approximate_oil_history_retains_range(){let s=service("s","engine-oil",Some((70000,74000)),None);let d=due("engine-oil",Some(&s),Some(81000),false,None);assert_eq!(d.status,"overdue");assert!(d.uncertain);assert_eq!(d.next_low_mm,Some(76000*MM_PER_MILE));assert_eq!(d.next_high_mm,Some(80000*MM_PER_MILE));assert_eq!(due("engine-oil",Some(&s),Some(78000),false,None).status,"unknownHistory");}
#[test]fn incomplete_service_date_cannot_prove_up_to_date(){let s=service("s","engine-oil",Some((70000,70000)),None);assert_eq!(due("engine-oil",Some(&s),Some(71000),false,None).status,"unknownHistory");}
#[test]fn initial_coolant_and_subsequent_intervals_are_distinct(){let mut s=service("factory","coolant",Some((0,0)),Some("2017-11-27"));s.factory_baseline=true;let d=due("coolant",Some(&s),Some(81000),false,None);assert_eq!(d.next_high_mm,Some(137500*MM_PER_MILE));assert_eq!(d.next_date.unwrap().text(),"2028-11-27");s.factory_baseline=false;s.mileage=Some(DistanceRange{low_mm:100000*MM_PER_MILE,high_mm:100000*MM_PER_MILE});assert_eq!(due("coolant",Some(&s),Some(170000),false,None).next_high_mm,Some(175000*MM_PER_MILE));}
#[test]fn severe_notes_are_not_invented_and_owner_override_is_explicit(){let s=service("s","engine-oil",Some((70000,70000)),Some("2026-09-01"));assert_eq!(due("engine-oil",Some(&s),Some(81000),true,None).status,"unknownHistory");let mut custom=Override{vehicle_id:"car".into(),item_id:"engine-oil".into(),interval:Interval{miles:Some(3000),months:None},disabled:false};let d=due("engine-oil",Some(&s),Some(74000),true,Some(&custom));assert_eq!(d.status,"overdue");assert!(d.explanation.contains("Owner interval"));custom.disabled=true;assert_eq!(due("engine-oil",None,None,true,Some(&custom)).status,"notApplicable");}
#[test]fn inspection_due_soon_is_distinguished(){let s=service("s","brake-lines",Some((60000,60000)),Some("2026-09-01"));assert_eq!(due("brake-lines",Some(&s),Some(71500),false,None).status,"inspectionRecommended");}
#[test]fn mileage_adds_only_trips_after_the_anchor(){let mut s=garage();s.readings.push(reading("r",100,81000));for(id,start,end,mi)in[("before",1,90,10),("cross",90,110,20),("after",110,200,240)]{s.trips.push(Trip{id:id.into(),vehicle_id:"car".into(),started_ms:start,ended_ms:end,distance_mm:mi*MM_PER_MILE,source:DistanceSource::Gps,notes:String::new()});}let m=own::mileage(&s,"car");assert_eq!(m.current_mm,Some(81240*MM_PER_MILE));assert!(m.estimated);assert_eq!(m.last_confirmed_mm,Some(81000*MM_PER_MILE));assert_eq!(m.excluded_partial_trips,1);}
#[test]fn newer_confirmed_reading_reconciles_previous_estimates(){let mut s=garage();s.readings.extend([reading("r1",1,81000),reading("r2",300,81220)]);s.trips.push(Trip{id:"trip".into(),vehicle_id:"car".into(),started_ms:10,ended_ms:200,distance_mm:240*MM_PER_MILE,source:DistanceSource::Gps,notes:String::new()});let m=own::mileage(&s,"car");assert_eq!(m.current_mm,Some(81220*MM_PER_MILE));assert!(!m.estimated);assert_eq!(m.added_trip_mm,0);}
#[test]fn incorrect_estimate_cannot_replace_confirmed_odometer(){let mut s=garage();s.readings.extend([reading("r1",1,81000),reading("r2",2,80000)]);s.readings[1].confidence=Confidence::Approximate;let m=own::mileage(&s,"car");assert_eq!(m.current_mm,Some(81000*MM_PER_MILE));assert!(!m.warnings.is_empty());}
#[test]fn corrected_measurements_remain_in_the_audit_trail(){let mut s=garage();s.readings.push(reading("old",100,82000));let mut r=reading("corrected",100,81000);r.supersedes=Some("old".into());r.notes="Correct transcription".into();s.readings.push(r);valid(&s).unwrap();assert_eq!(s.readings.len(),2);assert_eq!(own::mileage(&s,"car").current_mm,Some(81000*MM_PER_MILE));}
#[test]fn unrelated_correction_cannot_bypass_monotonic_readings(){let mut s=garage();s.readings.extend([reading("a",1,80000),reading("b",2,82000)]);let mut c=reading("c",3,81000);c.supersedes=Some("a".into());c.notes="Correction".into();s.readings.push(c);assert!(valid(&s).is_err());}
#[test]fn duplicate_times_gps_claims_and_cycles_are_rejected(){let mut s=garage();s.readings.extend([reading("a",1,80000),reading("b",1,81000)]);s.readings[1].confidence=Confidence::Approximate;assert!(valid(&s).is_err());s.readings.pop();s.readings[0].source=DistanceSource::Gps;assert!(valid(&s).is_err());s.readings.clear();let mut a=reading("a",1,80000);a.supersedes=Some("b".into());a.notes="reason".into();let mut b=reading("b",1,80000);b.supersedes=Some("a".into());b.notes="reason".into();s.readings.extend([a,b]);assert!(valid(&s).is_err());}
#[test]fn maximum_correction_chain_is_bounded_and_valid(){let mut s=garage();for n in 0..10000{let mut r=reading(&format!("r{n}"),n+1,81000);if n>0{r.supersedes=Some(format!("r{}",n-1));r.notes="Correction history".into();}s.readings.push(r);}valid(&s).unwrap();assert_eq!(own::mileage(&s,"car").anchor_id,Some("r9999".into()));}
#[test]fn partial_service_ordering_is_conservative(){let old=service("old","engine-oil",Some((70000,70000)),Some("2026-09-01"));let newer=service("new","engine-oil",Some((78000,78000)),None);assert_eq!(own::latest_service(&[&newer,&old]).unwrap().id,"new");let date_only=service("date","engine-oil",None,Some("2026-11-01"));assert!(own::latest_service(&[&date_only,&newer]).is_none());let contradictory=service("bad","engine-oil",Some((60000,60000)),Some("2026-11-01"));assert!(own::latest_service(&[&old,&contradictory]).is_none());}
#[test]fn ambiguous_history_is_reported_without_selecting_a_fake_latest_service(){let mut s=garage();s.readings.push(reading("r",1,81000));s.services.extend([service("a","engine-oil",None,Some("2026-10-01")),service("b","engine-oil",Some((79000,79000)),None)]);let out=execute(&s,json!({"type":"view"}));assert_eq!(out["summaries"][0]["maintenance"][0]["status"],"unknownHistory");assert!(out["summaries"][0]["maintenance"][0]["explanation"].as_str().unwrap().contains("reliably ordered"));}
#[test]fn full_to_full_fuel_includes_partial_fillups(){let mut s=garage();s.fuel.extend([fuel("a",1,80000,40000,true),fuel("b",2,80100,10000,false),fuel("c",3,80300,30000,true)]);let f=own::fuel_summary(&s,"car");assert_eq!(f.completed_cycles,1);assert_eq!(f.distance_mm,300*MM_PER_MILE);assert_eq!(f.volume_ml,40000);assert!((f.mpg_us.unwrap()-28.39058838).abs()<0.000001);}
#[test]fn partial_only_and_missed_fill_do_not_invent_economy(){let mut s=garage();s.fuel.extend([fuel("a",1,80000,10000,false),fuel("b",2,80100,10000,false)]);assert!(own::fuel_summary(&s,"car").mpg_us.is_none());s.fuel[0].full=true;s.fuel[1].full=true;s.fuel[1].missed_fill=true;assert_eq!(own::fuel_summary(&s,"car").completed_cycles,0);}
#[test]fn zero_distance_full_cycle_does_not_drop_fuel_consumption(){let mut s=garage();s.fuel.extend([fuel("a",1,80000,10000,true),fuel("b",2,80000,5000,true)]);let f=own::fuel_summary(&s,"car");assert_eq!(f.completed_cycles,1);assert_eq!(f.mpg_us,Some(0.0));assert!(f.litres_per_100_km.is_none());}
#[test]fn expenses_derive_from_service_and_fuel_without_duplicate_generated_rows(){let mut s=garage();s.fuel.push(fuel("f",1,80000,10000,true));let mut service=service("s","engine-oil",Some((80000,80000)),None);service.cost_cents=5000;s.services.push(service);s.expenses.push(Expense{id:"e".into(),vehicle_id:"car".into(),date:date("2026-11-01"),category:"Parking".into(),cost_cents:2000,financing:false,notes:String::new()});let out=execute(&s,json!({"type":"view"}));assert_eq!(out["summaries"][0]["costs"]["recordedOperatingCents"],8000);assert_eq!(out["state"]["expenses"].as_array().unwrap().len(),1);}
#[test]fn backup_merge_is_idempotent_and_conflicts_fail_atomically(){let mut s=garage();s.readings.push(reading("r",1,81000));let out=execute(&s,json!({"type":"mergeBackup","backup":s}));assert_eq!(out["changed"],false);assert_eq!(out["state"]["revision"],0);let mut bad=s.clone();bad.vehicles[0].nickname="Conflicting name".into();assert_eq!(execute(&s,json!({"type":"mergeBackup","backup":bad}))["ok"],false);assert_eq!(s.vehicles[0].nickname,"Legacy");}
#[test]fn invalid_imports_and_future_records_are_rejected(){let mut s=garage();s.readings.push(reading("r",1800000000001,81000));assert!(valid(&s).is_err());s.readings.clear();s.schema_version=2;assert!(valid(&s).is_err());s.schema_version=1;s.vehicles[0].year=2018;assert!(valid(&s).is_err());}
#[test]fn maintenance_profile_needs_an_exact_supported_engine(){let mut s=garage();s.vehicles[0].engine="12.5L".into();assert!(valid(&s).is_err());s.vehicles[0].engine="FB25".into();assert!(valid(&s).is_ok());s.vehicles[0].transmission="manual".into();assert!(valid(&s).is_err());s.vehicles[0].maintenance_profile=None;assert!(valid(&s).is_ok());}
#[test]fn trip_overlap_and_invalid_factory_cost_are_rejected(){let mut s=garage();for(id,start,end)in[("a",1,5),("b",4,7)]{s.trips.push(Trip{id:id.into(),vehicle_id:"car".into(),started_ms:start,ended_ms:end,distance_mm:MM_PER_MILE,source:DistanceSource::Gps,notes:String::new()});}assert!(valid(&s).is_err());s.trips.clear();let mut baseline=service("factory","coolant",Some((0,0)),Some("2017-01-01"));baseline.factory_baseline=true;baseline.cost_cents=1;s.services.push(baseline);assert!(valid(&s).is_err());}
#[test]fn json_boundary_rejects_unknown_fields_and_oversize(){let out:Value=serde_json::from_slice(&own::execute_json(&vec![b' ';own::MAX_JSON+1])).unwrap();assert_eq!(out["ok"],false);let mut req=json!({"state":garage(),"today":"2026-11-27","nowMs":1800000000000u64,"command":{"type":"view"},"unknown":true});assert_eq!(serde_json::from_slice::<Value>(&own::execute_json(&serde_json::to_vec(&req).unwrap())).unwrap()["ok"],false);req.as_object_mut().unwrap().remove("unknown");assert_eq!(serde_json::from_slice::<Value>(&own::execute_json(&serde_json::to_vec(&req).unwrap())).unwrap()["ok"],true);}
