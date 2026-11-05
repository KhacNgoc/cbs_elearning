import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from app import config, infrastructure, reindex


def point(point_id):
    return SimpleNamespace(id=point_id, payload={"section": "Mục", "heading": "Tiêu đề", "full_text": "Nội dung"})


class ReindexTests(unittest.IsolatedAsyncioTestCase):
    def client(self, target_exists, target_count, written=2):
        client = SimpleNamespace(
            collection_exists=AsyncMock(side_effect=lambda name: name == "old" or target_exists),
            count=AsyncMock(side_effect=[SimpleNamespace(count=target_count), SimpleNamespace(count=written)]),
            scroll=AsyncMock(return_value=([point("a"), point("b")], None)),
            delete_collection=AsyncMock(),
            create_collection=AsyncMock(),
            upsert=AsyncMock(),
        )
        return client

    def patched(self, client, vectors=None):
        embeddings = AsyncMock(return_value=vectors or [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]])
        return patch.object(infrastructure, "qdrant", client), patch.object(infrastructure, "embeddings", embeddings), embeddings

    async def test_copies_ids_and_payloads_with_the_configured_prefix(self):
        client = self.client(False, 0)
        qdrant, embed, embeddings = self.patched(client)
        client.count = AsyncMock(return_value=SimpleNamespace(count=2))
        with qdrant, embed, patch.object(config, "EMBEDDING_DOCUMENT_PREFIX", "doc: "):
            self.assertEqual(await reindex.reindex("old"), 2)
        self.assertEqual(embeddings.await_args.args, (["Tiêu đề\nNội dung", "Tiêu đề\nNội dung"], "doc: "))
        self.assertEqual(client.create_collection.await_args.kwargs["vectors_config"].size, 3)
        written = client.upsert.await_args.args[1].points
        self.assertEqual([p.id for p in written], ["a", "b"])
        self.assertEqual(written[0].payload["full_text"], "Nội dung")
        client.delete_collection.assert_not_awaited()

    async def test_refuses_to_replace_a_target_that_has_chunks(self):
        client = self.client(True, 5)
        qdrant, embed, embeddings = self.patched(client)
        with qdrant, embed, self.assertRaisesRegex(ValueError, "--overwrite"):
            await reindex.reindex("old")
        embeddings.assert_not_awaited()
        client.delete_collection.assert_not_awaited()

    async def test_refuses_to_read_and_write_the_same_collection(self):
        with self.assertRaisesRegex(ValueError, "both"):
            await reindex.reindex(config.COLLECTION)

    async def test_model_failure_leaves_an_existing_target_alone(self):
        client = self.client(True, 5)
        qdrant, embed, embeddings = self.patched(client)
        embeddings.side_effect = RuntimeError("model down")
        with qdrant, embed, self.assertRaises(RuntimeError):
            await reindex.reindex("old", overwrite=True)
        client.delete_collection.assert_not_awaited()

    async def test_reports_a_short_write(self):
        client = self.client(False, 0)
        client.count = AsyncMock(return_value=SimpleNamespace(count=1))
        qdrant, embed, _ = self.patched(client)
        with qdrant, embed, self.assertRaisesRegex(RuntimeError, "expected 2"):
            await reindex.reindex("old")


if __name__ == "__main__":
    unittest.main()
