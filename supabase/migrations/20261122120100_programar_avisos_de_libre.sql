select cron.schedule('near-push-free-alerts', '* * * * *', $job$
    select private.push_free_alerts();
    delete from cron.job_run_details
    where jobid = (select jobid from cron.job where jobname = 'near-push-free-alerts')
      and end_time < now() - interval '1 day';
$job$);
