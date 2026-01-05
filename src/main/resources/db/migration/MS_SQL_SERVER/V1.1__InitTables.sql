create table image_definition
(
    id                      uniqueidentifier
        constraint df_image_definition_id default newid(),
    name                    nvarchar(255)     not null,
    description             nvarchar(max),
    source                  varchar(max)      not null,
    metadata                varchar(max)      not null,
    license                 nvarchar(255),

    created_at              datetimeoffset(6) not null,
    updated_at              datetimeoffset(6) not null,

    container_port          int,

    last_built_image_id     uniqueidentifier,
    last_built_image_status nvarchar(32)      not null,

    constraint pk_image_definition primary key (id),
    constraint uq_image_definition_name unique (name),
    constraint chk_image_definition_source_is_json check (isjson(source) > 0),
    constraint chk_image_definition_metadata_is_json check (isjson(metadata) > 0)
);

create table image_definition_topics
(
    image_definition_id uniqueidentifier not null,
    topic_name          nvarchar(255)    not null,

    constraint pk_image_definition_topics primary key (image_definition_id, topic_name),
    constraint fk_image_definition_topics_to_image_definition foreign key (image_definition_id) references image_definition (id) on delete cascade
);

create table mcp_image_definition
(
    id        uniqueidentifier not null,
    transport nvarchar(64)     not null,

    constraint pk_mcp_image_definition primary key (id),
    constraint fk_mcp_image_definition_to_image_definition foreign key (id) references image_definition (id) on delete no action
);

create table nim_image_definition
(
    id uniqueidentifier not null,

    constraint pk_nim_image_definition primary key (id),
    constraint fk_nim_image_definition_to_image_definition foreign key (id) references image_definition (id) on delete no action
);

create table adapter_image_definition
(
    id uniqueidentifier not null,

    constraint pk_adapter_image_definition primary key (id),
    constraint fk_adapter_image_definition_to_image_definition foreign key (id) references image_definition (id) on delete no action
);

create table interceptor_image_definition
(
    id uniqueidentifier not null,

    constraint pk_interceptor_image_definition primary key (id),
    constraint fk_interceptor_image_definition_to_image_definition foreign key (id) references image_definition (id) on delete no action
);


create table built_image
(
    id                  uniqueidentifier
        constraint df_built_image_id default newid(),
    image_definition_id uniqueidentifier,
    source              varchar(max)      not null,
    metadata            varchar(max)      not null,

    status              nvarchar(32)      not null,
    image_name          nvarchar(255),
    created_at          datetimeoffset(6) not null,
    logs                varchar(max),

    constraint pk_built_image primary key (id),
    constraint fk_built_image_to_image_definition foreign key (image_definition_id) references image_definition (id) on delete set null,
    constraint chk_built_image_source_is_json check (isjson(source) > 0),
    constraint chk_built_image_metadata_is_json check (isjson(metadata) > 0),
    constraint chk_built_image_logs_is_json check (isjson(logs) > 0)
);

create table mcp_image
(
    id        uniqueidentifier not null,
    transport nvarchar(64)     not null,

    constraint pk_mcp_image primary key (id),
    constraint fk_mcp_image_to_built_image foreign key (id) references built_image (id) on delete no action
);


create table deployment
(
    id                  uniqueidentifier
        constraint df_deployment_id default newid(),
    image_id            uniqueidentifier,
    image_definition_id uniqueidentifier,

    name                nvarchar(255) not null,
    description         nvarchar(max),
    envs                varchar(max),
    initial_scale       int,
    min_scale           int,
    max_scale           int,
    container_port      int,
    status              nvarchar(32),
    url                 nvarchar(2048),
    resources           varchar(max),

    constraint pk_deployment primary key (id),
    constraint fk_deployment_to_built_image foreign key (image_id) references built_image (id) on delete set null,
    constraint fk_deployment_to_image_definition foreign key (image_definition_id) references image_definition (id) on delete set null,
    constraint chk_deployment_envs_is_json check (isjson(envs) > 0),
    constraint chk_deployment_resources_is_json check (isjson(resources) > 0)
);


create table disposable_resource
(
    id                 uniqueidentifier
        constraint df_disposable_resource_id default newid(),
    group_id           uniqueidentifier  not null,
    resource_reference varchar(max)      not null,
    lifecycle_state    nvarchar(32)      not null,
    created_at         datetimeoffset(6) not null,

    constraint pk_disposable_resource primary key (id),
    constraint chk_disposable_resource_reference_is_json check (isjson(resource_reference) > 0)
);

create table component_removal
(
    id         uniqueidentifier  not null,
    type       nvarchar(32)      not null,
    created_at datetimeoffset(6) not null,

    constraint pk_component_removal primary key (id, type)
);


create table shedlock
(
    name       nvarchar(64) not null,
    lock_until datetime2(3) null,
    locked_at  datetime2(3) null,
    locked_by  nvarchar(255),

    constraint pk_shedlock primary key (name)
);