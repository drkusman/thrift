alter table bad_debts add column current_balance bigint not null default 0;
update bad_debts set current_balance = original_amount where status = 'OUTSTANDING';
