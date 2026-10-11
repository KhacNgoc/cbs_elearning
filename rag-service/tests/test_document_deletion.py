import unittest
from unittest.mock import AsyncMock, patch
from qdrant_client import QdrantClient, models
from app.services.documents import delete_document


class DocumentDeletionTests(unittest.IsolatedAsyncioTestCase):
    async def test_deletion_is_scoped_to_both_document_and_course(self):
        client = QdrantClient(":memory:")
        client.create_collection("test", vectors_config=models.VectorParams(size=2, distance=models.Distance.COSINE))
        client.upsert("test", points=[
            models.PointStruct(id=i, vector=[1., 0.], payload={"course_id": course, "document_id": doc})
            for i, course, doc in [(1, "C1", "D1"), (2, "C1", "D2"), (3, "C2", "D1")]
        ])
        async def remove(**kwargs):
            self.assertTrue(kwargs["wait"])
            client.delete(**kwargs)
        qdrant = AsyncMock()
        qdrant.collection_exists.return_value = True
        qdrant.delete.side_effect = remove
        with patch("app.services.documents.infrastructure.qdrant", qdrant), patch("app.services.documents.config.COLLECTION", "test"):
            await delete_document("D1", "C1")
            await delete_document("D1", "C1")
        points, _ = client.scroll("test")
        self.assertEqual({2, 3}, {p.id for p in points})
        client.close()

    async def test_missing_collection_is_already_deleted(self):
        qdrant = AsyncMock()
        qdrant.collection_exists.return_value = False
        with patch("app.services.documents.infrastructure.qdrant", qdrant):
            await delete_document("D1", "C1")
        qdrant.delete.assert_not_awaited()
