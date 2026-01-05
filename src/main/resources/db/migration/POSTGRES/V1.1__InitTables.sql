-- This extension is needed for the gen_random_uuid() function.
create extension if not exists "pgcrypto";

create table image_definition
(
    id                      uuid default gen_random_uuid(),
    name                    varchar(255) not null,
    description             text,
    source                  jsonb        not null,
    metadata                jsonb        not null,
    license                 varchar(255),

    created_at              timestamp(3) not null,
    updated_at              timestamp(3) not null,

    container_port          integer,

    last_built_image_id     uuid,
    last_built_image_status varchar(32)  not null,

    constraint pk_image_definition primary key (id),
    constraint uq_image_definition_name unique (name)
);

create table image_definition_topics
(
    image_definition_id uuid         not null,
    topic_name          varchar(255) not null,

    constraint pk_image_definition_topics primary key (image_definition_id, topic_name),
    constraint fk_image_definition_topics_image_definition foreign key (image_definition_id) references image_definition (id) on delete cascade
);

create table mcp_image_definition
(
    id        uuid        not null,
    transport varchar(64) not null,

    constraint pk_mcp_image_definition primary key (id),
    constraint fk_mcp_image_definition_image_definition foreign key (id) references image_definition (id) on delete restrict
);

create table nim_image_definition
(
    id uuid not null,

    constraint pk_nim_image_definition primary key (id),
    constraint fk_nim_image_definition_image_definition foreign key (id) references image_definition (id) on delete restrict
);

create table adapter_image_definition
(
    id uuid not null,

    constraint pk_adapter_image_definition primary key (id),
    constraint fk_adapter_image_definition_image_definition foreign key (id) references image_definition (id) on delete restrict
);

create table interceptor_image_definition
(
    id uuid not null,

    constraint pk_interceptor_image_definition primary key (id),
    constraint fk_interceptor_image_definition_image_definition foreign key (id) references image_definition (id) on delete restrict
);


create table built_image
(
    id                  uuid default gen_random_uuid(),
    image_definition_id uuid,
    source              jsonb        not null,
    metadata            jsonb        not null,

    status              varchar(32)  not null,
    image_name          varchar(255),
    created_at          timestamp(3) not null,
    logs                jsonb,

    constraint pk_built_image primary key (id),
    constraint fk_built_image_image_definition foreign key (image_definition_id) references image_definition (id) on delete set null
);

create table mcp_image
(
    id        uuid        not null,
    transport varchar(64) not null,

    constraint pk_mcp_image primary key (id),
    constraint fk_mcp_image_built_image foreign key (id) references built_image (id) on delete restrict
);


create table deployment
(
    id                  uuid default gen_random_uuid(),
    image_id            uuid,
    image_definition_id uuid,

    name                varchar(255) not null,
    description         text,
    envs                jsonb,
    initial_scale       integer,
    min_scale           integer,
    max_scale           integer,
    container_port      integer,
    status              varchar(32),
    url                 varchar(2048),
    resources           jsonb,

    constraint pk_deployment primary key (id),
    constraint fk_deployment_built_image foreign key (image_id) references built_image (id) on delete set null,
    constraint fk_deployment_image_definition foreign key (image_definition_id) references image_definition (id) on delete set null
);


create table disposable_resource
(
    id                 uuid default gen_random_uuid(),
    group_id           uuid         not null,
    resource_reference jsonb        not null,
    lifecycle_state    varchar(32)  not null,
    created_at         timestamp(3) not null,

    constraint pk_disposable_resource primary key (id)
);

create table component_removal
(
    id         uuid         not null,
    type       varchar(32)  not null,
    created_at timestamp(3) not null,

    constraint pk_component_removal primary key (id, type)
);


create table shedlock
(
    name       varchar(64),
    lock_until timestamp(3) null,
    locked_at  timestamp(3) null,
    locked_by  varchar(255),

    constraint pk_shedlock primary key (name)
);