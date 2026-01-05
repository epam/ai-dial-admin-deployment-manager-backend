create table deployment
(
    id                  uuid default random_uuid(),
    image_id            uuid         not null,
    image_definition_id uuid         not null,

    name                varchar(255) not null,
    description         text,
    envs                json,
    initial_scale       integer,
    min_scale           integer,
    max_scale           integer,
    status              varchar(32),
    url                 varchar(2048),

    primary key (id),
    foreign key (image_id) references built_image (id) on delete restrict,
    foreign key (image_definition_id) references image_definition (id) on delete restrict
);