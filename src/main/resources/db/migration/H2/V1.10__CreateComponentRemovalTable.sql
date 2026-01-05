create table component_removal
(
    id         uuid         not null,
    type       varchar(32)  not null,
    created_at timestamp(3) not null,

    primary key (id, type)
);