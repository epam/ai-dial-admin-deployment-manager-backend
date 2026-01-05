create table built_image
(
    id                  uuid default random_uuid(),
    image_definition_id uuid         not null,
    source              json         not null,
    metadata            json         not null,

    status              varchar(32)  not null,
    image_name          varchar(255),
    created_at          timestamp(3) not null,
    logs                json,

    primary key (id),
    foreign key (image_definition_id) references image_definition (id) on delete restrict
);