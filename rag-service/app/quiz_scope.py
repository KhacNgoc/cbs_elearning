"""Strict source boundaries reuse existing document/page/section payloads."""
from qdrant_client import models
from app.schemas import QuizGenerationRequest


def quiz_source_filter(request: QuizGenerationRequest) -> models.Filter:
    conditions = [models.FieldCondition(
        key="course_id", match=models.MatchValue(value=request.course_id)
    )]
    if request.sources:
        alternatives = []
        for source in request.sources:
            must = [models.FieldCondition(
                key="document_id", match=models.MatchValue(value=source.document_id)
            )]
            if source.page_from is not None:
                must.append(models.FieldCondition(
                    key="page", range=models.Range(gte=source.page_from, lte=source.page_to)
                ))
            if source.section is not None:
                must.append(models.FieldCondition(
                    key="section", match=models.MatchValue(value=source.section)
                ))
            alternatives.append(models.Filter(must=must))
        # OR between source selections; document and its bounds stay together.
        conditions.append(models.Filter(should=alternatives))
    return models.Filter(must=conditions)
