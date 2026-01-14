create table if not exists image_definition_topics
(
    image_definition_id uuid         not null,
    topic_name          varchar(255) not null,

    constraint pk_image_definition_topics primary key (image_definition_id, topic_name),
    constraint fk_image_definition_topics_image_definition foreign key (image_definition_id) references image_definition (id) on delete cascade
);
