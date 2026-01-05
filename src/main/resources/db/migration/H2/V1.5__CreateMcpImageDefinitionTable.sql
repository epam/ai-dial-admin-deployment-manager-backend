create table mcp_image_definition
(
    id              uuid        not null,
    transport       varchar(64) not null,

    primary key (id),
    foreign key (id) references image_definition (id) on delete restrict
);