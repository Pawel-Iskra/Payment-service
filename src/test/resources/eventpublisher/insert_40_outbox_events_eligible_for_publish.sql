DELETE FROM outbox_event;
INSERT INTO outbox_event (event_id,
                          aggregate_id,
                          correlation_id,
                          event_type,
                          payload,
                          created_at,
                          attempt_count,
                          next_attempt_at)
SELECT gen_random_uuid(),
       gen_random_uuid(),
       gen_random_uuid(),
       'PAYMENT_CREATED_EVENT',
       '{"eventId":"c32d0cae-a082-4633-95c8-add8fd4a8b41"}',
       NOW() - INTERVAL '2' MINUTE,
       0,
       NOW()
FROM generate_series(1, 40);