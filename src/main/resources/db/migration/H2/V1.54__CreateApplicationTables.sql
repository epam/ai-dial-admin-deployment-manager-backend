create table application_image_definition
(
    id              uuid        not null,

    primary key (id),
    foreign key (id) references image_definition (id) on delete restrict
);

create table application_deployment
(
    id              varchar(36) not null,

    primary key (id),
    foreign key (id) references deployment (id) on delete restrict
);
