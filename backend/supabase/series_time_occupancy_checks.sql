-- Read-only date fixtures; no user task or account data is accessed.
with cases(label,anchored,z,day,minute,at,expected) as (values
 ('history crossing Berlin midnight',true,'Europe/Berlin',date '2026-11-08',30,extract(epoch from timestamptz '2026-11-08 00:30:00+05:30')*1000,date '2026-11-07'),
 ('history crossing Tokyo midnight',true,'Asia/Tokyo',date '2026-11-07',1410,extract(epoch from timestamptz '2026-11-07 23:30:00+01:00')*1000,date '2026-11-08'),
 ('date-only history',true,'Europe/Berlin',date '2026-11-08',null,0,date '2026-11-08'),
 ('ordinary timed date',true,'Europe/Berlin',date '2026-11-08',1169,extract(epoch from timestamptz '2026-11-08 19:29:00+01:00')*1000,date '2026-11-08'),
 ('floating template preserves prior date-only rule',false,'UTC',date '2026-11-08',30,extract(epoch from timestamptz '2026-11-08 00:30:00+05:30')*1000,date '2026-11-08')
)
select label,
 case when anchored and at>0 and minute is not null
 then (to_timestamp(at/1000.0) at time zone z)::date else day end=expected as passed
from cases;
