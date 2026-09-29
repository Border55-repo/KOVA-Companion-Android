// Use local calendar dates rather than UTC, including around midnight and DST.
export function dateKey(date){
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}
export function monthDays(month){
  const [year,number]=month.split('-').map(Number);
  const first=new Date(year,number-1,1,12);
  const offset=(first.getDay()+6)%7;
  const length=new Date(year,number,0,12).getDate();
  return [...Array(offset).fill(null),...Array.from({length},(_,i)=>dateKey(new Date(year,number-1,i+1,12)))];
}
export function moveMonth(month,step){
  const [year,number]=month.split('-').map(Number);
  return dateKey(new Date(year,number-1+step,1,12)).slice(0,7);
}
