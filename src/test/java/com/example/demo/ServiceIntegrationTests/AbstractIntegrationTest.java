package com.example.demo.ServiceIntegrationTests;

import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.FolderRepository;
import com.example.demo.repository.FolderShareRepository;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.2.0"))
            .withServices(
                    LocalStackContainer.Service.S3,
                    LocalStackContainer.Service.DYNAMODB
            );

    static {
        localstack.start();
    }

    @DynamicPropertySource
    static void registerAwsProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.endpoint", () -> localstack.getEndpoint().toString());
    }

    // Mocken, damit er nicht versucht, echte JWTs zu validieren
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    protected FolderRepository folderRepository;

    @Autowired
    protected FolderShareRepository shareRepository;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    protected FileMetadataRepository fileMetadataRepository;

    @Autowired
    protected CognitoIdentityProviderClient cognitoClient;

    @BeforeAll
    static void setupAwsResources() {
        // 1. S3 Bucket erstellen
        try (S3Client s3 = buildS3Client()) {
            try {
                s3.createBucket(b -> b.bucket("test-bucket-name"));
            } catch (Exception ignored) {}
        }

        // 2. DynamoDB Client & Tabellen
        try (DynamoDbClient dynamoDb = buildDynamoDbClient()) {
            // --- Tabelle 1: Folder ---
            createTable(dynamoDb, "Folder", "folderId", null,
                    List.of(createGSI("UserIndex", "userId", null)));

            // --- Tabelle 2: FileMetadata ---
            createTable(dynamoDb, "FileMetadata", "fileId", null,
                    List.of(createGSI("FolderIndex", "folderId", null)));

            // --- Tabelle 3: FolderShare ---
            createTable(dynamoDb, "FolderShare", "userId", "folderId",
                    List.of(createGSI("gsi_folder_lookup", "folderId", "userId")));
        }
    }

    // --- Helper Clients für das Setup (Static) ---

    private static S3Client buildS3Client() {
        return S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();
    }

    private static DynamoDbClient buildDynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();
    }

    // --- Tabellen Erstellung ---

    private static void createTable(DynamoDbClient ddb, String tableName, String pkName, String skName, List<GlobalSecondaryIndex> gsis) {
        try {
            // 1. Attribute definieren (PK und SK)
            List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
            attributeDefinitions.add(AttributeDefinition.builder().attributeName(pkName).attributeType(ScalarAttributeType.S).build());
            if (skName != null) {
                attributeDefinitions.add(AttributeDefinition.builder().attributeName(skName).attributeType(ScalarAttributeType.S).build());
            }

            // 2. Attribute für GSIs hinzufügen
            if (gsis != null) {
                for (GlobalSecondaryIndex gsi : gsis) {
                    String gsiPk = gsi.keySchema().get(0).attributeName();
                    if (attributeDefinitions.stream().noneMatch(a -> a.attributeName().equals(gsiPk))) {
                        attributeDefinitions.add(AttributeDefinition.builder().attributeName(gsiPk).attributeType(ScalarAttributeType.S).build());
                    }
                    if (gsi.keySchema().size() > 1) {
                        String gsiSk = gsi.keySchema().get(1).attributeName();
                        if (attributeDefinitions.stream().noneMatch(a -> a.attributeName().equals(gsiSk))) {
                            attributeDefinitions.add(AttributeDefinition.builder().attributeName(gsiSk).attributeType(ScalarAttributeType.S).build());
                        }
                    }
                }
            }

            // 3. Key Schema
            List<KeySchemaElement> keySchema = new ArrayList<>();
            keySchema.add(KeySchemaElement.builder().attributeName(pkName).keyType(KeyType.HASH).build());
            if (skName != null) {
                keySchema.add(KeySchemaElement.builder().attributeName(skName).keyType(KeyType.RANGE).build());
            }

            // 4. Create Request
            CreateTableRequest.Builder requestBuilder = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(keySchema)
                    .attributeDefinitions(attributeDefinitions)
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build());

            if (gsis != null && !gsis.isEmpty()) {
                requestBuilder.globalSecondaryIndexes(gsis);
            }

            ddb.createTable(requestBuilder.build());
            System.out.println("Tabelle erstellt: " + tableName);

        } catch (ResourceInUseException ignored) {
        }
    }

    private static GlobalSecondaryIndex createGSI(String indexName, String pkName, String skName) {
        List<KeySchemaElement> keySchema = new ArrayList<>();
        keySchema.add(KeySchemaElement.builder().attributeName(pkName).keyType(KeyType.HASH).build());
        if (skName != null) {
            keySchema.add(KeySchemaElement.builder().attributeName(skName).keyType(KeyType.RANGE).build());
        }

        return GlobalSecondaryIndex.builder()
                .indexName(indexName)
                .keySchema(keySchema)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .build();
    }

    // --- Cleanup Helper für @AfterEach in Tests ---

    protected void cleanDatabase() {
        clearTable("Folder", "folderId", null);
        clearTable("FileMetadata", "fileId", null);
        clearTable("FolderShare", "userId", "folderId");
    }

    private void clearTable(String tableName, String pkName, String skName) {
        try {
            ScanRequest scanRequest = ScanRequest.builder().tableName(tableName).build();
            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

            for (Map<String, AttributeValue> item : scanResponse.items()) {
                DeleteItemRequest.Builder deleteBuilder = DeleteItemRequest.builder()
                        .tableName(tableName)
                        .key(buildKey(item, pkName, skName));

                dynamoDbClient.deleteItem(deleteBuilder.build());
            }
        } catch (ResourceNotFoundException e) {
        }
    }

    private Map<String, AttributeValue> buildKey(Map<String, AttributeValue> item, String pkName, String skName) {
        Map<String, AttributeValue> key = new java.util.HashMap<>();
        key.put(pkName, item.get(pkName));
        if (skName != null) {
            key.put(skName, item.get(skName));
        }
        return key;
    }
}