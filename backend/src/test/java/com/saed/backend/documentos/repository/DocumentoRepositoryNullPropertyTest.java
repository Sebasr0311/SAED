package com.saed.backend.documentos.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saed.backend.documentos.repository.impl.DocumentoRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * An organization administrator has no active property. Binding a null Long to a "? IS NULL" predicate makes Oracle
 * fail with a database error (HTTP 500), so the null must never reach the driver as a bind variable.
 */
class DocumentoRepositoryNullPropertyTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DocumentoRepositoryImpl repository = new DocumentoRepositoryImpl(jdbc);

    @Test
    void findAllByPropiedadWithoutPropertyDoesNotBindNull() {
        repository.findAllByPropiedad(null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), args.capture());
        assertThat(sql.getValue()).doesNotContain("? IS NULL");
        assertThat(args.getValue()).isEmpty();
    }

    @Test
    void findPublicosByPropiedadWithoutPropertyDoesNotBindNull() {
        repository.findPublicosByPropiedad(null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), args.capture());
        assertThat(sql.getValue()).doesNotContain("? IS NULL");
        assertThat(args.getValue()).isEmpty();
    }

    @Test
    void findAllByPropiedadWithPropertyFiltersByProperty() {
        repository.findAllByPropiedad(7L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), args.capture());
        assertThat(sql.getValue()).contains("D.ID_PROPIEDAD = ?");
        assertThat(args.getValue()).containsExactly(7L);
    }

    @Test
    void updateDocumentoWithoutPropertyDoesNotBindNull() {
        com.saed.backend.documentos.dto.DocumentoDTO doc = new com.saed.backend.documentos.dto.DocumentoDTO();
        doc.setTitulo("Reglamento");
        doc.setCategoria("REGLAMENTO");
        doc.setDescripcion("Texto");
        repository.updateDocumento(5L, doc, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue()).doesNotContain("? IS NULL");
        assertThat(args.getValue()).doesNotContainNull().endsWith(5L);
    }

    @Test
    void deleteDocumentoWithoutPropertyDoesNotBindNull() {
        repository.deleteDocumento(5L, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue()).doesNotContain("? IS NULL");
        assertThat(args.getValue()).containsExactly(5L);
    }

    @Test
    void deleteDocumentoWithPropertyFiltersByProperty() {
        repository.deleteDocumento(5L, 7L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("D.ID_PROPIEDAD = ?");
        assertThat(args.getValue()).containsExactly(5L, 7L);
    }
}
