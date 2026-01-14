create table disposable_resource
(
    id                 uuid default random_uuid(),
    group_id           uuid         not null,
    resource_reference json         not null,
    lifecycle_state    varchar(32)  not null,
    created_at         timestamp(3) not null,

    primary key (id)
);