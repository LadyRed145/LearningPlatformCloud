package com.duoc.learningplatformcloud.service;

import com.duoc.learningplatformcloud.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class S3ResumenService {

    private static final String CONTENT_TYPE_TEXT = "text/plain; charset=utf-8";

    public static final String KEY_EVIDENCIA_RABBITMQ = "mq/evidencia-rabbitmq.txt";

    private static final List<String> KEYS_RABBITMQ_OBSOLETAS = List.of(
            "mq/ultimo-envio.json",
            "mq/estado-cola.json",
            "mq/ultimo-consumo.json",
            "mq/resumenes-consumidos.json"
    );

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String subirResumen(Long resumenId, Path archivoResumen) {
        validarParametros(resumenId, archivoResumen);

        String key = construirKey(resumenId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(CONTENT_TYPE_TEXT)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(archivoResumen));

        return key;
    }

    public String actualizarResumen(Long resumenId, Path archivoResumen) {
        validarParametros(resumenId, archivoResumen);

        String key = construirKey(resumenId);
        validarExistencia(key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(CONTENT_TYPE_TEXT)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(archivoResumen));

        return key;
    }

    public String sobrescribirEvidenciaRabbitMq(String contenidoTexto) {
        eliminarEvidenciasRabbitMqObsoletas();
        return subirTexto(KEY_EVIDENCIA_RABBITMQ, contenidoTexto);
    }

    public String subirTexto(String key, String contenido) {
        validarKey(key);

        String contenidoSeguro = Objects.requireNonNullElse(contenido, "");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(CONTENT_TYPE_TEXT)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(contenidoSeguro.getBytes(StandardCharsets.UTF_8))
        );

        return key;
    }

    public ByteArrayResource descargarResumen(Long resumenId) {
        if (resumenId == null) {
            throw new IllegalArgumentException("El ID del resumen no puede ser nulo.");
        }

        String key = construirKey(resumenId);
        validarExistencia(key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        ResponseBytes<GetObjectResponse> archivo = s3Client.getObjectAsBytes(request);

        byte[] contenido = Objects.requireNonNull(
                archivo.asByteArray(),
                "El archivo descargado desde S3 no puede ser nulo."
        );

        return new ByteArrayResource(contenido);
    }

    public void eliminarResumen(Long resumenId) {
        if (resumenId == null) {
            throw new IllegalArgumentException("El ID del resumen no puede ser nulo.");
        }

        String key = construirKey(resumenId);
        validarExistencia(key);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(request);
    }

    public String construirNombreArchivo(Long resumenId) {
        if (resumenId == null) {
            throw new IllegalArgumentException("El ID del resumen no puede ser nulo.");
        }

        return "Resumen_" + resumenId + ".txt";
    }

    private String construirKey(Long resumenId) {
        return resumenId + "/" + construirNombreArchivo(resumenId);
    }

    private void eliminarEvidenciasRabbitMqObsoletas() {
        KEYS_RABBITMQ_OBSOLETAS.forEach(this::eliminarObjetoSiExiste);
    }

    private void eliminarObjetoSiExiste(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);

        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
        }
    }

    private void validarKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("La key de S3 no puede ser nula ni vacía.");
        }
    }

    private void validarParametros(Long resumenId, Path archivoResumen) {
        if (resumenId == null) {
            throw new IllegalArgumentException("El ID del resumen no puede ser nulo.");
        }

        if (archivoResumen == null) {
            throw new IllegalArgumentException("El archivo del resumen no puede ser nulo.");
        }

        if (!Files.exists(archivoResumen)) {
            throw new IllegalArgumentException("El archivo del resumen no existe: " + archivoResumen);
        }

        if (!Files.isRegularFile(archivoResumen)) {
            throw new IllegalArgumentException("La ruta indicada no corresponde a un archivo válido: " + archivoResumen);
        }
    }

    private void validarExistencia(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(request);

        } catch (NoSuchKeyException ex) {
            throw new RecursoNoEncontradoException("El archivo no existe en S3: " + key);

        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new RecursoNoEncontradoException("El archivo no existe en S3: " + key);
            }

            throw ex;
        }
    }
}
