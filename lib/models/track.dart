class Track {
  final int id;
  final String title;
  final String artist;
  final String album;
  final String cover;
  final String audio;
  final String duration;
  final bool explicit;
  final bool aiGenerated;

  const Track({
    required this.id,
    required this.title,
    required this.artist,
    required this.album,
    required this.cover,
    required this.audio,
    required this.duration,
    this.explicit = false,
    this.aiGenerated = false,
  });

  factory Track.fromJson(Map<String, dynamic> json, Uri base) {
    String url(String value) {
      if (value.isEmpty) return '';
      return base.resolve(value).toString();
    }

    final durationValue = json['duration']?.toString() ?? '0:00';
    return Track(
      id: int.tryParse('${json['id'] ?? 0}') ?? 0,
      title: json['title']?.toString() ?? 'Без названия',
      artist: json['artist']?.toString() ?? 'Неизвестный исполнитель',
      album: json['album']?.toString() ?? 'Сингл',
      cover: url(json['cover']?.toString() ?? ''),
      audio: url(json['audio']?.toString() ?? ''),
      duration: durationValue,
      explicit: '${json['is_explicit'] ?? 0}' == '1',
      aiGenerated: '${json['is_ai_generated'] ?? 0}' == '1',
    );
  }
}
