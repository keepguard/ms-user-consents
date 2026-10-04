--
-- PostgreSQL database dump
--

-- Dumped from database version 15.5 (Debian 15.5-1.pgdg120+1)
-- Dumped by pg_dump version 15.5 (Debian 15.5-1.pgdg120+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: ms_user_consents; Type: SCHEMA; Schema: -; Owner: keepguard_api_user
--

CREATE SCHEMA ms_user_consents;


ALTER SCHEMA ms_user_consents OWNER TO keepguard_api_user;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: consent_documents; Type: TABLE; Schema: ms_user_consents; Owner: keepguard_api_user
--

CREATE TABLE ms_user_consents.consent_documents (
    id uuid NOT NULL,
    content_hash character varying(64),
    created_at timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    description character varying(500),
    file_size_bytes bigint,
    mime_type character varying(100),
    published_at timestamp(6) without time zone,
    s3_key character varying(512),
    status character varying(16) NOT NULL,
    title character varying(255) NOT NULL,
    type character varying(50) NOT NULL,
    updated_by character varying(255),
    version integer NOT NULL,
    company_id uuid,
    tenant_id uuid,
    CONSTRAINT consent_documents_status_check CHECK (((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('PUBLISHED'::character varying)::text, ('ARCHIVED'::character varying)::text]))),
    CONSTRAINT consent_documents_type_check CHECK (((type)::text = ANY (ARRAY[('TERMS_OF_USE'::character varying)::text, ('PRIVACY_POLICY'::character varying)::text, ('LGPD_COMPLIANCE'::character varying)::text, ('DATA_PROCESSING'::character varying)::text, ('ESSENTIAL_COOKIES'::character varying)::text, ('ANALYTICS'::character varying)::text, ('PERFORMANCE_COOKIES'::character varying)::text, ('MARKETING_EMAIL'::character varying)::text, ('MARKETING_SMS'::character varying)::text, ('MARKETING_PUSH'::character varying)::text, ('THIRD_PARTY_SHARING'::character varying)::text, ('PROFILING'::character varying)::text])))
);


ALTER TABLE ms_user_consents.consent_documents OWNER TO keepguard_api_user;

--
-- Name: user_consents; Type: TABLE; Schema: ms_user_consents; Owner: keepguard_api_user
--

CREATE TABLE ms_user_consents.user_consents (
    id uuid NOT NULL,
    accepted_at timestamp(6) without time zone NOT NULL,
    consent_document_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(255) NOT NULL,
    geolocation character varying(100),
    ip_address character varying(45),
    user_agent character varying(512),
    user_id uuid NOT NULL,
    version integer NOT NULL,
    company_id uuid,
    revocation_reason character varying(255),
    revoked_at timestamp(6) without time zone,
    tenant_id uuid
);


ALTER TABLE ms_user_consents.user_consents OWNER TO keepguard_api_user;

--
-- Name: consent_documents consent_documents_pkey; Type: CONSTRAINT; Schema: ms_user_consents; Owner: keepguard_api_user
--

ALTER TABLE ONLY ms_user_consents.consent_documents
    ADD CONSTRAINT consent_documents_pkey PRIMARY KEY (id);


--
-- Name: user_consents user_consents_pkey; Type: CONSTRAINT; Schema: ms_user_consents; Owner: keepguard_api_user
--

ALTER TABLE ONLY ms_user_consents.user_consents
    ADD CONSTRAINT user_consents_pkey PRIMARY KEY (id);


--
-- Name: idx_user_consents_company_user_doc; Type: INDEX; Schema: ms_user_consents; Owner: keepguard_api_user
--

CREATE INDEX idx_user_consents_company_user_doc ON ms_user_consents.user_consents USING btree (company_id, user_id, consent_document_id);


--
-- Name: user_consents fkklvtwtvbuaimgirwha2lcymn8; Type: FK CONSTRAINT; Schema: ms_user_consents; Owner: keepguard_api_user
--

ALTER TABLE ONLY ms_user_consents.user_consents
    ADD CONSTRAINT fkklvtwtvbuaimgirwha2lcymn8 FOREIGN KEY (consent_document_id) REFERENCES ms_user_consents.consent_documents(id);


--
-- PostgreSQL database dump complete
--

