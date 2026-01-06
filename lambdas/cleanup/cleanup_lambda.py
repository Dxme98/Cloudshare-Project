import os
import boto3
from boto3.dynamodb.conditions import Key
from boto3.dynamodb.types import TypeDeserializer

s3 = boto3.client('s3')
dynamodb = boto3.resource('dynamodb')
deserializer = TypeDeserializer()

METADATA_TABLE = os.environ['METADATA_TABLE_NAME']
SHARE_TABLE = os.environ['SHARE_TABLE_NAME']
BUCKET_NAME = os.environ['S3_BUCKET_NAME']

def lambda_handler(event, context):
    for record in event['Records']:
        if record['eventName'] == 'REMOVE':
            try:
                # Saubereres Parsen von DynamoDB JSON zu Python Dict
                old_image = {k: deserializer.deserialize(v) for k, v in record['dynamodb']['OldImage'].items()}
                folder_id = old_image.get('folderId')

                if not folder_id:
                    print("Skipping: No folderId found in OldImage")
                    continue

                print(f"--- CLEANUP START: {folder_id} ---")
                delete_files_and_metadata(folder_id)
                delete_folder_shares(folder_id)
                print(f"--- CLEANUP DONE: {folder_id} ---")

            except Exception as e:
                print(f"CRITICAL ERROR processing record: {str(e)}")

def delete_files_and_metadata(folder_id):
    table = dynamodb.Table(METADATA_TABLE)
    last_evaluated_key = None

    while True:
        query_args = {
            'IndexName': 'FolderIndex',
            'KeyConditionExpression': Key('folderId').eq(folder_id)
        }
        if last_evaluated_key:
            query_args['ExclusiveStartKey'] = last_evaluated_key

        response = table.query(**query_args)
        items = response.get('Items', [])

        if not items:
            break

        # Listen sammeln für Batch-Operationen
        s3_keys_to_delete = []

        with table.batch_writer() as batch:
            for item in items:
                file_id = item['fileId']

                # 1. Sammeln für S3 (nur wenn Key existiert)
                if item.get('s3Key'):
                    s3_keys_to_delete.append({'Key': item['s3Key']})

                # 2. Löschen aus DynamoDB
                batch.delete_item(Key={'fileId': file_id})

        # 3. S3 Batch Delete ausführen (Viel schneller!)
        # delete_objects erlaubt max 1000 keys pro call, aber unser Batch Loop
        # wird durch DynamoDB Pages begrenzt (meistens ~1MB), also sicherheitshalber chunken
        if s3_keys_to_delete:
            delete_s3_batch(s3_keys_to_delete)

        last_evaluated_key = response.get('LastEvaluatedKey')
        if not last_evaluated_key:
            break

def delete_s3_batch(keys):
    """Hilfsfunktion um S3 Objekte in 1000er Chunks zu löschen"""
    # S3 delete_objects Limit ist 1000
    chunk_size = 1000
    for i in range(0, len(keys), chunk_size):
        chunk = keys[i:i + chunk_size]
        try:
            s3.delete_objects(
                Bucket=BUCKET_NAME,
                Delete={'Objects': chunk}
            )
            print(f"Deleted {len(chunk)} files from S3.")
        except Exception as e:
            print(f"Error batch deleting S3 objects: {e}")

def delete_folder_shares(folder_id):
    table = dynamodb.Table(SHARE_TABLE)
    last_evaluated_key = None

    while True:
        query_args = {
            'IndexName': 'gsi_folder_lookup',
            'KeyConditionExpression': Key('folderId').eq(folder_id)
        }
        if last_evaluated_key:
            query_args['ExclusiveStartKey'] = last_evaluated_key

        response = table.query(**query_args)

        with table.batch_writer() as batch:
            for item in response.get('Items', []):
                batch.delete_item(Key={
                    'userId': item['userId'],
                    'folderId': item['folderId']
                })

        last_evaluated_key = response.get('LastEvaluatedKey')
        if not last_evaluated_key:
            break