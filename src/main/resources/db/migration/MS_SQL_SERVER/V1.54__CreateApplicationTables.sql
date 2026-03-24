create table application_image_definition
(
    id uniqueidentifier not null,

    constraint pk_application_image_definition primary key (id),
    constraint fk_application_image_definition_to_image_definition foreign key (id) references image_definition (id) on delete no action
);

create table application_deployment
(
    id varchar(36) not null,

    constraint pk_application_deployment primary key (id),
    constraint fk_application_deployment_to_deployment foreign key (id) references deployment (id) on delete no action
);
