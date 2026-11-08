-- Support bulk due-time changes that re-anchor a daily series to the editor's clock.
-- Preserve historical timestamps and existing membership, RLS and write guards.
-- Only the generator's occupied-date comparison changes; no task data is rewritten.
do $patch$
declare d text;
begin
 d:=pg_get_functiondef('private.expand_daily_series(uuid,timestamptz)'::regprocedure);
 if position('series-time occupied date' in d)>0 then return; end if;
 if position('and c.payload->>''seriesId''=r.id::text and (c.payload->>''day'')::bigint=d)' in d)=0 then
  raise exception 'Unexpected daily generator definition; review before changing it';
 end if;
 d:=replace(d,
  'and c.payload->>''seriesId''=r.id::text and (c.payload->>''day'')::bigint=d)',
  'and c.payload->>''seriesId''=r.id::text and case -- series-time occupied date
     when anchor is not null and coalesce((c.payload->>''scheduledAt'')::bigint,0)>0 and c.payload->>''minute'' is not null
     then (to_timestamp((c.payload->>''scheduledAt'')::bigint/1000.0) at time zone z)::date-date ''1970-01-01''
     else (c.payload->>''day'')::bigint end=d)');
 execute d;
end $patch$;
