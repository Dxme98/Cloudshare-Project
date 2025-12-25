import boto3
import os

# Clients initialisieren (wie deine @Autowired Templates in Java)
s3 = boto3.client('s3')
dynamodb = boto3.resource('dynamodb')

# Tabellen- und Bucket-Namen aus Umgebungsvariablen (via Terraform gesetzt)
METADATA_TABLE = os.environ['METADATA_TABLE_NAME']
BUCKET_NAME = os.environ['S3_BUCKET_NAME']

def lambda_handler(event, context):
    for record in event['Records']:
        # Wir reagieren NUR auf Löschungen (REMOVE)
        if record['eventName'] == 'REMOVE':
            # Die folderId aus dem "OldImage" holen
            old_image = record['dynamodb']['OldImage']
            folder_id = old_image['folderId']['S']

            print(f"Starte Cleanup für gelöschten Ordner: {folder_id}")

            cleanup_folder_contents(folder_id)

def cleanup_folder_contents(folder_id):
    table = dynamodb.Table(METADATA_TABLE)

    # 1. Alle Dateien des Ordners über den GSI finden
    response = table.query(
        IndexName='FolderIndex',
        KeyConditionExpression=boto3.dynamodb.conditions.Key('folderId').eq(folder_id)
    )

    items = response.get('Items', [])

    for item in items:
        file_id = item['fileId']
        s3_key = item['s3Key']

        try:
            # 2. Physische Datei in S3 löschen
            s3.delete_object(Bucket=BUCKET_NAME, Key=s3_key)
            print(f"S3 gelöscht: {s3_key}")

            # 3. Metadaten-Eintrag löschen
            table.delete_item(Key={'fileId': file_id})
            print(f"Metadaten gelöscht: {file_id}")

        except Exception as e:
            print(f"Fehler beim Löschen von {file_id}: {str(e)}")