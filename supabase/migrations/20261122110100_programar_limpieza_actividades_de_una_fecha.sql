-- One-off activities that ended a month ago no longer show anywhere; remove them daily so they do
-- not count toward the 200 activities per person.
select cron.schedule('near-purge-past-one-off', '17 4 * * *', $job$
    delete from public.personal_schedule where one_off_date < (now() at time zone 'UTC')::date - 30;
    delete from cron.job_run_details
    where jobid = (select jobid from cron.job where jobname = 'near-purge-past-one-off')
      and end_time < now() - interval '7 days';
$job$);
