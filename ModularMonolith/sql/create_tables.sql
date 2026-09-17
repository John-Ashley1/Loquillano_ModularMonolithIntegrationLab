-- Run this in the Supabase SQL editor (Project > SQL Editor > New query)
-- This script recreates the FULL current schema from scratch, including
-- Lab 2's changes (multi-item orders, cancellation, notifications).

drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id varchar(20) primary key,
    name       varchar(100) not null,
    stock      integer not null default 0 check (stock >= 0)
);

-- orders no longer carries product_id/quantity directly - an order can now
-- have multiple line items, held in order_items below. status now also
-- supports CANCELLED alongside CONFIRMED/REJECTED.
create table orders (
    order_id   bigserial primary key,
    status     varchar(20) not null,
    reason     varchar(255),
    created_at timestamp not null default now()
);

create table order_items (
    order_item_id bigserial primary key,
    order_id      bigint not null references orders(order_id),
    product_id    varchar(20) not null references inventory(product_id),
    quantity      integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigserial primary key,
    message          varchar(500) not null,
    created_at       timestamp not null default now()
);

-- Seed data as specified in the lab
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do nothing;
