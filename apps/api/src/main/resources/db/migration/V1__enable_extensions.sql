-- btree_gist permite combinar igualdad (business_id, staff_id) y solapamiento de rangos
-- en una misma restricción EXCLUDE: es la base de la protección contra la doble reserva.
CREATE EXTENSION IF NOT EXISTS btree_gist;
