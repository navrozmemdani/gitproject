import pytest

from app.core.config import Settings
from app.core.errors import FileTooLargeError, SuspiciousFileError, UnsupportedMediaError
from app.services.file_validation import sniff_video, validate_image_bytes, validate_video_header
from tests.conftest import fixture_bytes

S = Settings(_env_file=None)


@pytest.mark.parametrize("name,kind", [("sample_car.jpg", "jpeg"), ("sample_car.png", "png"), ("sample_car.webp", "webp")])
def test_accepts_supported_formats(name, kind):
    v = validate_image_bytes(fixture_bytes(name), name, None, S)
    assert v.kind == kind
    assert (v.width, v.height) == (640, 480)
    assert v.sanitized_jpeg[:3] == b"\xff\xd8\xff"
    assert v.exif.get("has_gps") is False


def test_jpeg_extension_alias():
    assert validate_image_bytes(fixture_bytes("sample_car.jpg"), "photo.JPEG", "image/jpg", S).kind == "jpeg"


def test_rejects_gif():
    with pytest.raises(UnsupportedMediaError):
        validate_image_bytes(b"GIF89a" + b"\x00" * 100, "a.gif", "image/gif", S)


def test_rejects_extension_mismatch():
    with pytest.raises(SuspiciousFileError):
        validate_image_bytes(fixture_bytes("sample_car.png"), "photo.jpg", None, S)


def test_rejects_mime_mismatch():
    with pytest.raises(SuspiciousFileError):
        validate_image_bytes(fixture_bytes("sample_car.png"), "photo.png", "image/jpeg", S)


def test_rejects_polyglot_payload():
    with pytest.raises(SuspiciousFileError):
        validate_image_bytes(fixture_bytes("polyglot.jpg"), "polyglot.jpg", None, S)


def test_rejects_html_disguised_as_image():
    with pytest.raises(UnsupportedMediaError):
        validate_image_bytes(fixture_bytes("not_an_image.jpg"), "not_an_image.jpg", None, S)


def test_rejects_truncated_image():
    data = fixture_bytes("sample_car.png")[:400]
    with pytest.raises(UnsupportedMediaError):
        validate_image_bytes(data, "x.png", None, S)


def test_rejects_large_image():
    small = Settings(_env_file=None, max_image_bytes=1000)
    with pytest.raises(FileTooLargeError):
        validate_image_bytes(fixture_bytes("sample_car.jpg"), "a.jpg", None, small)


def test_rejects_empty():
    with pytest.raises(UnsupportedMediaError):
        validate_image_bytes(b"", "a.jpg", None, S)


def test_video_sniffing():
    mp4 = fixture_bytes("sample_video.mp4")
    assert sniff_video(mp4[:64]) == "mp4"
    assert sniff_video(b"\x1a\x45\xdf\xa3" + b"\x00" * 20) == "webm"
    assert sniff_video(b"\x00\x00\x00\x14ftypqt  " + b"\x00" * 8) == "mov"
    assert validate_video_header(mp4[:64], "clip.mov", "video/quicktime", len(mp4), S).kind == "mov"


def test_video_rejects_unknown_and_large():
    with pytest.raises(UnsupportedMediaError):
        validate_video_header(b"RIFF....AVI ", "clip.avi", None, 100, S)
    with pytest.raises(FileTooLargeError):
        validate_video_header(b"\x1a\x45\xdf\xa3", "a.webm", None, S.max_video_bytes + 1, S)
    with pytest.raises(SuspiciousFileError):
        validate_video_header(b"\x1a\x45\xdf\xa3" + b"\x00" * 10, "a.mp4", None, 100, S)
