/** Combine published calendar state with newer Bridge checks for the same korps. */
export function latestCalendarCheck(row, ...checks){
  let latest={...row};
  for(const check of checks){
    const next=Date.parse(check?.checkedAt);
    const current=Date.parse(latest.lastCheckedAt);
    if(!Number.isFinite(next)||(Number.isFinite(current)&&next<current))continue;
    latest={...latest,lastCheckedAt:check.checkedAt,status:check.status||latest.status,error:check.error||null};
  }
  return latest;
}
