CREATE TABLE IF NOT EXISTS public.customer (
    customer_id bigint NOT NULL,
    ssn character varying(20),
    first_name character varying(200),
    last_name character varying(200),
	CONSTRAINT customer_pk PRIMARY KEY (customer_id)
);
ALTER TABLE public.customer OWNER TO "CRM_USER";

CREATE TABLE IF NOT EXISTS public.address (
    customer_id bigint,
    address_id bigint NOT NULL,
    street_address_1 character varying(200),
    street_address_2 character varying(200),
    city character varying(200),
    zip character varying(200),
    state character varying(200),
    country character varying(200),
	CONSTRAINT address_pk PRIMARY KEY (address_id),
	CONSTRAINT address_fk1 FOREIGN KEY (customer_id) REFERENCES public.customer(customer_id)
);
ALTER TABLE public.address OWNER TO "CRM_USER";

CREATE TABLE IF NOT EXISTS public.contract (
    customer_id bigint,
    contract_id bigint NOT NULL,
    contract_ref_id bigint,
    associated_line character varying(200),
    contract_description character varying(200),
    from_date timestamp without time zone,
    to_date timestamp without time zone,
    associated_line_fmt character varying(200),
	CONSTRAINT contract_pk PRIMARY KEY (contract_id),
	CONSTRAINT contract_fk1 FOREIGN KEY (customer_id) REFERENCES public.customer(customer_id)
);
ALTER TABLE public.contract OWNER TO "CRM_USER";

CREATE TABLE IF NOT EXISTS public.activity (
    customer_id bigint,
    activity_id bigint NOT NULL,
    activity_date timestamp without time zone,
    activity_note character varying(2000),
	CONSTRAINT activity_pk PRIMARY KEY (activity_id),
	CONSTRAINT activity_fk1 FOREIGN KEY (customer_id) REFERENCES public.customer(customer_id)
);
ALTER TABLE public.activity OWNER TO "CRM_USER";

CREATE TABLE IF NOT EXISTS public.cases (
    activity_id bigint,
    case_id bigint NOT NULL,
    case_date timestamp without time zone,
    case_type character varying(200),
    status character varying(200),
	CONSTRAINT case_pk PRIMARY KEY (case_id),
	CONSTRAINT case_fk1 FOREIGN KEY (activity_id) REFERENCES public.activity(activity_id)
);
ALTER TABLE public.cases OWNER TO "CRM_USER";

CREATE TABLE IF NOT EXISTS public.case_note (
    case_id bigint,
    note_id bigint NOT NULL,
    note_date timestamp without time zone,
    note_text character varying(3000),
	CONSTRAINT note_pk PRIMARY KEY (note_id),
	CONSTRAINT case_note_fk1 FOREIGN KEY (case_id) REFERENCES public.cases(case_id)
);
ALTER TABLE public.case_note OWNER TO "CRM_USER";