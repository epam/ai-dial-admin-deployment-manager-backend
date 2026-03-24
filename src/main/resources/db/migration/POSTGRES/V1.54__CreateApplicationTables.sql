create table application_image_definition
(
    id uuid not null,

    constraint pk_application_image_definition primary key (id),
    constraint fk_application_image_definition_image_definition foreign key (id) references image_definition (id) on delete restrict
);

create table application_deployment
(
    id varchar(36) not null,

    constraint pk_application_deployment primary key (id),
    constraint fk_application_deployment_deployment foreign key (id) references deployment (id) on delete restrict
);
