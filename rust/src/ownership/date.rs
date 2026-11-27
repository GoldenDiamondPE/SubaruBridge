use serde::{Deserialize, Serialize};

/// Calendar dates are local civil dates, not fixed 30-day month approximations.
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(try_from = "String", into = "String")]
pub struct Date { pub year: i32, pub month: u32, pub day: u32 }
impl Date {
    pub fn parse(value: &str) -> Result<Self, String> {
        if value.len()!=10 || value.as_bytes()[4]!=b'-' || value.as_bytes()[7]!=b'-' { return Err("Use a YYYY-MM-DD date".into()); }
        let pieces: Vec<_> = value.split('-').collect();
        if pieces.len()!=3 || pieces.iter().any(|s| !s.bytes().all(|c| c.is_ascii_digit())) { return Err("Invalid date".into()); }
        let year=pieces[0].parse().map_err(|_| "Invalid year")?;
        let month=pieces[1].parse().map_err(|_| "Invalid month")?;
        let day=pieces[2].parse().map_err(|_| "Invalid day")?;
        if !(1900..=2200).contains(&year) || !(1..=12).contains(&month) || day==0 || day>Self::month_days(year,month) {return Err("Invalid calendar date".into());}
        Ok(Self{year,month,day})
    }
    fn month_days(year:i32,month:u32)->u32 { match month {4|6|9|11=>30,2=>if year%4==0 && (year%100!=0 || year%400==0){29}else{28},_=>31} }
    pub fn add_months(&self, months:u32)->Result<Self,String> {
        if months>2400{return Err("Interval exceeds 200 years".into());}
        let total=self.year*12+self.month as i32-1+months as i32;
        let year=total/12;let month=(total%12+1) as u32;
        if year>2200{return Err("Date exceeds supported calendar".into());}
        Ok(Self{year,month,day:self.day.min(Self::month_days(year,month))})
    }
    pub fn ordinal(&self)->i64 {
        let mut days=0i64;
        for year in 1900..self.year {days+=if year%4==0 && (year%100!=0 || year%400==0){366}else{365};}
        for month in 1..self.month {days+=Self::month_days(self.year,month) as i64;}
        days+self.day as i64-1
    }
    pub fn text(&self)->String {format!("{:04}-{:02}-{:02}",self.year,self.month,self.day)}
}
impl TryFrom<String> for Date {type Error=String;fn try_from(value:String)->Result<Self,String>{Self::parse(&value)}}
impl From<Date> for String {fn from(value:Date)->String{value.text()}}
