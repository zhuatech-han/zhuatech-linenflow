-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
CREATE TABLE customer (id bigint AUTO_INCREMENT PRIMARY KEY, name varchar(120) NOT NULL UNIQUE, department_id bigint NOT NULL, enabled boolean NOT NULL, FOREIGN KEY(department_id) REFERENCES department(id));
ALTER TABLE account ADD COLUMN customer_id bigint;
ALTER TABLE account ADD CONSTRAINT fk_account_customer FOREIGN KEY(customer_id) REFERENCES customer(id);
CREATE TABLE linen_item (
id bigint AUTO_INCREMENT PRIMARY KEY,
name varchar(120) NOT NULL,
category varchar(60) NOT NULL,
enabled boolean NOT NULL
);
CREATE TABLE rate_card (
id bigint AUTO_INCREMENT PRIMARY KEY,
customer_id bigint NOT NULL,
item_id bigint NOT NULL,
unit_price decimal(12,2) NOT NULL,
reference varchar(200) NOT NULL,
enabled boolean NOT NULL
);
CREATE TABLE laundry_batch (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(80) NOT NULL,
customer_id bigint NOT NULL,
customer_name varchar(120) NOT NULL,
department_id bigint NOT NULL,
author_id bigint NOT NULL,
service_date date NOT NULL,
note varchar(1000) NOT NULL,
status varchar(30) NOT NULL,
version bigint NOT NULL,
processor_id bigint,
count_note varchar(1000) NOT NULL,
quality_note varchar(1000) NOT NULL,
completed_at timestamp(6),
invoice_id bigint
);
CREATE TABLE batch_line (
id bigint AUTO_INCREMENT PRIMARY KEY,
batch_id bigint NOT NULL,
item_id bigint NOT NULL,
item_name varchar(120) NOT NULL,
unit_price decimal(12,2) NOT NULL,
price_reference varchar(200) NOT NULL,
declared_qty int NOT NULL,
received_qty int NOT NULL,
good_qty int NOT NULL,
rewash_qty int NOT NULL,
discard_qty int NOT NULL,
signed_qty int NOT NULL
);
CREATE TABLE delivery (
id bigint AUTO_INCREMENT PRIMARY KEY,
batch_id bigint NOT NULL,
dispatcher_id bigint NOT NULL,
reference varchar(200) NOT NULL,
status varchar(30) NOT NULL,
note varchar(1000) NOT NULL,
signer_id bigint,
returner_id bigint,
created_at timestamp(6) NOT NULL
);
CREATE TABLE delivery_line (
id bigint AUTO_INCREMENT PRIMARY KEY,
delivery_id bigint NOT NULL,
batch_line_id bigint NOT NULL,
sent_qty int NOT NULL,
accepted_qty int NOT NULL
);
CREATE TABLE monthly_invoice (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(80) NOT NULL,
customer_id bigint NOT NULL,
customer_name varchar(120) NOT NULL,
department_id bigint NOT NULL,
period varchar(7) NOT NULL,
status varchar(30) NOT NULL,
version bigint NOT NULL,
amount decimal(16,2) NOT NULL,
note varchar(1000) NOT NULL,
author_id bigint NOT NULL,
created_at timestamp(6) NOT NULL
);
CREATE TABLE payment (
id bigint AUTO_INCREMENT PRIMARY KEY,
invoice_id bigint NOT NULL,
amount decimal(16,2) NOT NULL,
reference varchar(200) NOT NULL,
actor_id bigint NOT NULL,
created_at timestamp(6) NOT NULL,
reversed_by bigint,
reversal_reference varchar(200),
reversal_note varchar(1000)
);
CREATE TABLE business_event (
id bigint AUTO_INCREMENT PRIMARY KEY,
object_type varchar(30) NOT NULL,
object_id bigint NOT NULL,
actor_id bigint NOT NULL,
action varchar(60) NOT NULL,
note varchar(1000) NOT NULL,
snapshot longtext NOT NULL,
created_at timestamp(6) NOT NULL
);
CREATE TABLE command_record (id bigint AUTO_INCREMENT PRIMARY KEY, request_key varchar(36) NOT NULL UNIQUE, fingerprint varchar(64) NOT NULL, result_id bigint NOT NULL);
ALTER TABLE rate_card ADD CONSTRAINT fk_rate_card_customer_id FOREIGN KEY(customer_id) REFERENCES customer(id);
ALTER TABLE rate_card ADD CONSTRAINT fk_rate_card_item_id FOREIGN KEY(item_id) REFERENCES linen_item(id);
ALTER TABLE laundry_batch ADD CONSTRAINT fk_laundry_batch_customer_id FOREIGN KEY(customer_id) REFERENCES customer(id);
ALTER TABLE laundry_batch ADD CONSTRAINT fk_laundry_batch_department_id FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE laundry_batch ADD CONSTRAINT fk_laundry_batch_author_id FOREIGN KEY(author_id) REFERENCES account(id);
ALTER TABLE laundry_batch ADD CONSTRAINT fk_laundry_batch_processor_id FOREIGN KEY(processor_id) REFERENCES account(id);
ALTER TABLE laundry_batch ADD CONSTRAINT fk_laundry_batch_invoice_id FOREIGN KEY(invoice_id) REFERENCES monthly_invoice(id);
ALTER TABLE batch_line ADD CONSTRAINT fk_batch_line_batch_id FOREIGN KEY(batch_id) REFERENCES laundry_batch(id);
ALTER TABLE batch_line ADD CONSTRAINT fk_batch_line_item_id FOREIGN KEY(item_id) REFERENCES linen_item(id);
ALTER TABLE delivery ADD CONSTRAINT fk_delivery_batch_id FOREIGN KEY(batch_id) REFERENCES laundry_batch(id);
ALTER TABLE delivery ADD CONSTRAINT fk_delivery_dispatcher_id FOREIGN KEY(dispatcher_id) REFERENCES account(id);
ALTER TABLE delivery ADD CONSTRAINT fk_delivery_signer_id FOREIGN KEY(signer_id) REFERENCES account(id);
ALTER TABLE delivery ADD CONSTRAINT fk_delivery_returner_id FOREIGN KEY(returner_id) REFERENCES account(id);
ALTER TABLE delivery_line ADD CONSTRAINT fk_delivery_line_delivery_id FOREIGN KEY(delivery_id) REFERENCES delivery(id);
ALTER TABLE delivery_line ADD CONSTRAINT fk_delivery_line_batch_line_id FOREIGN KEY(batch_line_id) REFERENCES batch_line(id);
ALTER TABLE monthly_invoice ADD CONSTRAINT fk_monthly_invoice_customer_id FOREIGN KEY(customer_id) REFERENCES customer(id);
ALTER TABLE monthly_invoice ADD CONSTRAINT fk_monthly_invoice_department_id FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE monthly_invoice ADD CONSTRAINT fk_monthly_invoice_author_id FOREIGN KEY(author_id) REFERENCES account(id);
ALTER TABLE payment ADD CONSTRAINT fk_payment_invoice_id FOREIGN KEY(invoice_id) REFERENCES monthly_invoice(id);
ALTER TABLE payment ADD CONSTRAINT fk_payment_actor_id FOREIGN KEY(actor_id) REFERENCES account(id);
ALTER TABLE payment ADD CONSTRAINT fk_payment_reversed_by FOREIGN KEY(reversed_by) REFERENCES account(id);
ALTER TABLE business_event ADD CONSTRAINT fk_business_event_actor_id FOREIGN KEY(actor_id) REFERENCES account(id);
CREATE UNIQUE INDEX ux_batch_code ON laundry_batch(code);
CREATE UNIQUE INDEX ux_invoice_code ON monthly_invoice(code);
CREATE UNIQUE INDEX ux_rate_customer_item ON rate_card(customer_id,item_id);
CREATE UNIQUE INDEX ux_batch_item ON batch_line(batch_id,item_id);
CREATE UNIQUE INDEX ux_delivery_line ON delivery_line(delivery_id,batch_line_id);
CREATE UNIQUE INDEX ux_payment_reference ON payment(reference);
CREATE UNIQUE INDEX ux_payment_reversal ON payment(reversal_reference);
CREATE UNIQUE INDEX ux_delivery_reference ON delivery(reference);
CREATE INDEX ix_batch_scope ON laundry_batch(department_id,customer_id,status);
CREATE INDEX ix_invoice_scope ON monthly_invoice(department_id,customer_id,period);
CREATE INDEX ix_event_object ON business_event(object_type,object_id,id);
ALTER TABLE batch_line ADD CONSTRAINT ck_counts CHECK (declared_qty>0 AND received_qty>=0 AND good_qty>=0 AND rewash_qty>=0 AND discard_qty>=0 AND signed_qty>=0 AND signed_qty<=good_qty);
ALTER TABLE delivery_line ADD CONSTRAINT ck_delivery CHECK (sent_qty>0 AND accepted_qty>=0 AND accepted_qty<=sent_qty);
ALTER TABLE rate_card ADD CONSTRAINT ck_rate CHECK (unit_price>0);
ALTER TABLE payment ADD CONSTRAINT ck_payment CHECK (amount>0);
CREATE TABLE invoice_batch (id bigint AUTO_INCREMENT PRIMARY KEY, invoice_id bigint NOT NULL, batch_id bigint NOT NULL, amount decimal(16,2) NOT NULL, UNIQUE(invoice_id,batch_id), FOREIGN KEY(invoice_id) REFERENCES monthly_invoice(id), FOREIGN KEY(batch_id) REFERENCES laundry_batch(id));
