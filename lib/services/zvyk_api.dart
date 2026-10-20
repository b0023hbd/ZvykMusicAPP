import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config.dart';
import '../models/track.dart';

class ZvykApi {
  final Uri base = Uri.parse(siteBaseUrl);

  /// The provided PHP homepage embeds the published catalogue in
  /// window.ZVYK_TRACKS. This reads that same server-generated catalogue,
  /// so the app uses your site's data instead of a hard-coded demo list.
  Future<List<Track>> fetchTracks() async {
    final response = await http.get(
      base,
      headers: const {'Accept': 'text/html,application/xhtml+xml'},
    ).timeout(const Duration(seconds: 20));

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Сайт вернул HTTP ${response.statusCode}');
    }

    final html = utf8.decode(response.bodyBytes);
    final match = RegExp(
      r'window\.ZVYK_TRACKS\s*=\s*(\[.*?\]);\s*window\.ZVYK_CSRF',
      dotAll: true,
    ).firstMatch(html);

    if (match == null) {
      throw Exception(
        'Не найден каталог ZVYK_TRACKS. Проверь адрес сайта и доступность главной страницы.',
      );
    }

    final decoded = jsonDecode(match.group(1)!);
    if (decoded is! List) throw Exception('Каталог сайта имеет неверный формат.');

    return decoded
        .whereType<Map>()
        .map((item) => Track.fromJson(
              Map<String, dynamic>.from(item),
              base,
            ))
        .where((track) => track.audio.isNotEmpty)
        .toList();
  }
}
