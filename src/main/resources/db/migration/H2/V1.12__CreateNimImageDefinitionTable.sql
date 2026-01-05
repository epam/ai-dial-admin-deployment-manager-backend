create table nim_image_definition
(
    id uuid not null,

    primary key (id),
    foreign key (id) references image_definition (id) on delete restrict
);