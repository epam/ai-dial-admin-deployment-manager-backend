create table mcp_image
(
    id        uuid        not null,
    transport varchar(64) not null,

    primary key (id),
    foreign key (id) references built_image (id) on delete restrict
);

insert into mcp_image (id, transport)
select bi.id, 'SSE'
from built_image as bi
where exists (
    select 1
    from mcp_image_definition as mid
    where mid.id = bi.image_definition_id
);
