create table shedlock
(
    name        varchar(64),
    lock_until  timestamp(3) null,
    locked_at   timestamp(3) null,
    locked_by   varchar(255),

    primary key (name)
);