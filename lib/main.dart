import 'dart:async';
import 'dart:math' as math;
import 'dart:ui';

import 'package:audio_session/audio_session.dart';
import 'package:audio_service/audio_service.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';

import 'config.dart';
import 'models/track.dart';
import 'services/zvyk_api.dart';

const Color ink = Color(0xFF090A12);
const Color violet = Color(0xFF9B7BFF);
const Color cyan = Color(0xFF7DE8FF);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await JustAudioBackground.init(
    androidNotificationChannelId: 'ru.zvykmusic.playback',
    androidNotificationChannelName: 'ZvykMusic playback',
    androidNotificationOngoing: true,
  );
  final session = await AudioSession.instance;
  await session.configure(const AudioSessionConfiguration.music());
  runApp(const ZvykMusicApp());
}

class ZvykMusicApp extends StatelessWidget {
  const ZvykMusicApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'ZvykMusic',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          brightness: Brightness.dark,
          scaffoldBackgroundColor: ink,
          colorScheme: ColorScheme.fromSeed(
            seedColor: violet,
            brightness: Brightness.dark,
            surface: const Color(0xFF171724),
          ),
          useMaterial3: true,
          fontFamily: 'Inter',
        ),
        home: const MusicHome(),
      );
}

class MusicHome extends StatefulWidget {
  const MusicHome({super.key});
  @override
  State<MusicHome> createState() => _MusicHomeState();
}

class _MusicHomeState extends State<MusicHome> with TickerProviderStateMixin {
  final ZvykApi api = ZvykApi();
  final AudioPlayer player = AudioPlayer();
  final TextEditingController search = TextEditingController();
  late final AnimationController pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1700),
  )..repeat(reverse: true);

  List<Track> tracks = [];
  List<Track> queue = [];
  int selectedTab = 0;
  int currentIndex = -1;
  bool loading = true;
  String? error;
  String query = '';
  StreamSubscription<PlayerState>? stateSub;

  Track? get current =>
      currentIndex >= 0 && currentIndex < queue.length ? queue[currentIndex] : null;

  List<Track> get filtered {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return tracks;
    return tracks.where((t) =>
      t.title.toLowerCase().contains(q) ||
      t.artist.toLowerCase().contains(q) ||
      t.album.toLowerCase().contains(q)).toList();
  }

  @override
  void initState() {
    super.initState();
    _load();
    player.currentIndexStream.listen((index) {
      if (index != null && mounted) setState(() => currentIndex = index);
    });
    stateSub = player.playerStateStream.listen((_) {
      if (mounted) setState(() {});
    });
  }

  Future<void> _load() async {
    setState(() { loading = true; error = null; });
    try {
      final result = await api.fetchTracks();
      if (!mounted) return;
      setState(() { tracks = result; loading = false; });
    } catch (e) {
      if (!mounted) return;
      setState(() { loading = false; error = e.toString().replaceFirst('Exception: ', ''); });
    }
  }

  Future<void> playTrack(Track track, {List<Track>? from}) async {
    final source = from ?? tracks;
    final index = source.indexWhere((t) => t.id == track.id);
    final list = index < 0 ? [track] : source;
    final start = index < 0 ? 0 : index;
    try {
      queue = list;
      currentIndex = start;
      final sources = list.map((t) => AudioSource.uri(
        Uri.parse(t.audio),
        tag: MediaItem(
          id: '${t.id}',
          title: t.title,
          artist: t.artist,
          album: t.album,
          artUri: t.cover.isEmpty ? null : Uri.tryParse(t.cover),
        ),
      )).toList();
      await player.setAudioSource(
        ConcatenatingAudioSource(children: sources),
        initialIndex: start,
      );
      await player.play();
      if (mounted) setState(() {});
    } catch (e) {
      if (mounted) _message('Не удалось запустить трек: $e');
    }
  }

  Future<void> togglePlayback() async {
    if (player.playing) {
      await player.pause();
    } else if (player.audioSource == null && tracks.isNotEmpty) {
      await playTrack(tracks.first);
    } else {
      await player.play();
    }
  }

  void _message(String text) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(text), behavior: SnackBarBehavior.floating),
    );
  }

  @override
  void dispose() {
    stateSub?.cancel();
    player.dispose();
    search.dispose();
    pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(children: [
        const Positioned.fill(child: AmbientBackground()),
        SafeArea(
          child: Column(children: [
            _topBar(),
            Expanded(child: _body()),
            if (current != null) _miniPlayer(),
            _bottomBar(),
          ]),
        ),
      ]),
    );
  }

  Widget _topBar() => Padding(
    padding: const EdgeInsets.fromLTRB(20, 12, 20, 14),
    child: Row(children: [
      Container(
        width: 42, height: 42,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(15),
          gradient: const LinearGradient(colors: [violet, cyan]),
          boxShadow: [BoxShadow(color: violet.withOpacity(.3), blurRadius: 22)],
        ),
        child: const Icon(Icons.graphic_eq_rounded, color: ink, size: 27),
      ),
      const SizedBox(width: 12),
      const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Zvykmusic', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800, letterSpacing: -.6)),
        Text('ТВОЯ МУЗЫКА. ТВОЯ АТМОСФЕРА.', style: TextStyle(fontSize: 9, letterSpacing: 1.5, color: Colors.white54)),
      ])),
      GlassIconButton(icon: Icons.refresh_rounded, onTap: _load),
    ]),
  );

  Widget _body() {
    if (loading) return const Center(child: CircularProgressIndicator(color: violet));
    if (error != null) {
      return Center(child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.cloud_off_rounded, size: 52, color: Colors.white54),
          const SizedBox(height: 16),
          const Text('Не получилось подключиться к Zvykmusic', textAlign: TextAlign.center,
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Text(error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white60)),
          const SizedBox(height: 18),
          FilledButton.icon(onPressed: _load, icon: const Icon(Icons.refresh), label: const Text('Повторить')),
          const SizedBox(height: 10),
          const Text('Адрес сайта задаётся в lib/config.dart', style: TextStyle(color: Colors.white38, fontSize: 12)),
        ]),
      ));
    }
    if (tracks.isEmpty) return const Center(child: Text('На сайте пока нет доступных треков.'));
    return RefreshIndicator(
      color: violet,
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 22),
        children: [
          if (selectedTab == 0) ...[
            const Text('ТВОЙ ЗВУК', style: TextStyle(color: cyan, letterSpacing: 2.4, fontSize: 10, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            const Text('Погрузись\nв музыку.', style: TextStyle(fontSize: 36, height: 1.05, fontWeight: FontWeight.w800, letterSpacing: -1.3)),
            const SizedBox(height: 18),
            _heroCard(),
            const SizedBox(height: 26),
            _sectionHeader('Для тебя', '${tracks.length} треков'),
          ] else if (selectedTab == 1) ...[
            const Text('ПОИСК', style: TextStyle(color: cyan, letterSpacing: 2.4, fontSize: 10, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            const Text('Найди свой звук', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
            const SizedBox(height: 16),
            TextField(
              controller: search,
              onChanged: (v) => setState(() => query = v),
              decoration: InputDecoration(
                hintText: 'Трек, артист или альбом',
                prefixIcon: const Icon(Icons.search_rounded),
                filled: true,
                fillColor: Colors.white.withOpacity(.07),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: BorderSide(color: Colors.white.withOpacity(.1))),
                enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: BorderSide(color: Colors.white.withOpacity(.1))),
              ),
            ),
            const SizedBox(height: 18),
          ] else ...[
            const Text('ТВОЯ КОЛЛЕКЦИЯ', style: TextStyle(color: cyan, letterSpacing: 2.4, fontSize: 10, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            const Text('Вся музыка', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            const Text('Каталог загружается с твоего сайта.', style: TextStyle(color: Colors.white54)),
            const SizedBox(height: 18),
          ],
          ...((selectedTab == 1) ? filtered : tracks).asMap().entries.map((entry) =>
            TrackTile(
              track: entry.value,
              index: entry.key,
              active: current?.id == entry.value.id,
              playing: player.playing && current?.id == entry.value.id,
              onTap: () => playTrack(entry.value, from: selectedTab == 1 ? filtered : tracks),
            )),
        ],
      ),
    );
  }

  Widget _heroCard() {
    final featured = tracks.first;
    return GestureDetector(
      onTap: () => playTrack(featured),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(30),
        child: Stack(children: [
          SizedBox(height: 205, width: double.infinity, child: featured.cover.isEmpty
            ? const ColoredBox(color: Color(0xFF26233C))
            : CachedNetworkImage(imageUrl: featured.cover, fit: BoxFit.cover,
                errorWidget: (_, __, ___) => const ColoredBox(color: Color(0xFF26233C)))),
          Positioned.fill(child: DecoratedBox(decoration: BoxDecoration(
            gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter,
              colors: [Colors.black.withOpacity(.04), const Color(0xE6090A12)])))),
          Positioned.fill(child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 0, sigmaY: 0),
            child: const SizedBox())),
          Positioned(left: 20, right: 20, bottom: 18, child: Row(children: [
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('СЕЙЧАС В ТВОЁМ ПОТОКЕ', style: TextStyle(fontSize: 9, letterSpacing: 1.7, color: cyan, fontWeight: FontWeight.w700)),
              const SizedBox(height: 5),
              Text(featured.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
              Text(featured.artist, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white70)),
            ])),
            Container(
              width: 52, height: 52,
              decoration: const BoxDecoration(shape: BoxShape.circle, gradient: LinearGradient(colors: [violet, cyan])),
              child: const Icon(Icons.play_arrow_rounded, color: ink, size: 32),
            ),
          ])),
        ]),
      ),
    );
  }

  Widget _sectionHeader(String title, String trailing) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: Row(children: [
      Text(title, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
      const Spacer(),
      Text(trailing, style: const TextStyle(color: Colors.white38, fontSize: 12)),
    ]),
  );

  Widget _miniPlayer() => Padding(
    padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
    child: GestureDetector(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => NowPlayingPage(
          player: player, getCurrent: () => current,
          onNext: () => player.seekToNext(), onPrevious: () => player.seekToPrevious(),
          pulse: pulse,
        ),
      )),
      child: GlassPanel(
        radius: 22,
        padding: const EdgeInsets.all(10),
        child: Row(children: [
          CoverArt(track: current!, size: 48, radius: 14),
          const SizedBox(width: 11),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(current!.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
            Text(current!.artist, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white54, fontSize: 12)),
          ])),
          IconButton(onPressed: () => player.seekToPrevious(), icon: const Icon(Icons.skip_previous_rounded)),
          IconButton(onPressed: togglePlayback, icon: Icon(player.playing ? Icons.pause_rounded : Icons.play_arrow_rounded, size: 30)),
          IconButton(onPressed: () => player.seekToNext(), icon: const Icon(Icons.skip_next_rounded)),
        ]),
      ),
    ),
  );

  Widget _bottomBar() => Padding(
    padding: const EdgeInsets.fromLTRB(18, 0, 18, 12),
    child: GlassPanel(
      radius: 26,
      padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 8),
      child: Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [
        _navItem(0, Icons.home_rounded, 'Главная'),
        _navItem(1, Icons.search_rounded, 'Поиск'),
        _navItem(2, Icons.library_music_rounded, 'Медиатека'),
      ]),
    ),
  );

  Widget _navItem(int index, IconData icon, String label) => GestureDetector(
    onTap: () => setState(() => selectedTab = index),
    child: AnimatedContainer(
      duration: const Duration(milliseconds: 220),
      padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: selectedTab == index ? Colors.white.withOpacity(.12) : Colors.transparent,
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, color: selectedTab == index ? cyan : Colors.white54, size: 21),
        const SizedBox(height: 3),
        Text(label, style: TextStyle(fontSize: 10, color: selectedTab == index ? Colors.white : Colors.white54, fontWeight: selectedTab == index ? FontWeight.w700 : FontWeight.w400)),
      ]),
    ),
  );
}

class NowPlayingPage extends StatefulWidget {
  final AudioPlayer player;
  final Track? Function() getCurrent;
  final VoidCallback onNext;
  final VoidCallback onPrevious;
  final AnimationController pulse;
  const NowPlayingPage({super.key, required this.player, required this.getCurrent, required this.onNext, required this.onPrevious, required this.pulse});
  @override
  State<NowPlayingPage> createState() => _NowPlayingPageState();
}

class _NowPlayingPageState extends State<NowPlayingPage> {
  String time(Duration? d) {
    if (d == null) return '0:00';
    final s = d.inSeconds;
    return '${s ~/ 60}:${(s % 60).toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final track = widget.getCurrent();
    if (track == null) return const Scaffold(body: Center(child: Text('Нет выбранного трека')));
    return Scaffold(
      body: Stack(children: [
        const Positioned.fill(child: AmbientBackground()),
        SafeArea(child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 10, 22, 20),
          child: Column(children: [
            Row(children: [
              GlassIconButton(icon: Icons.keyboard_arrow_down_rounded, onTap: () => Navigator.pop(context)),
              const Spacer(),
              const Text('СЕЙЧАС ИГРАЕТ', style: TextStyle(fontSize: 10, letterSpacing: 2.1, color: Colors.white60, fontWeight: FontWeight.w700)),
              const Spacer(),
              const SizedBox(width: 44),
            ]),
            const Spacer(),
            AnimatedBuilder(
              animation: widget.pulse,
              builder: (_, child) => Transform.scale(scale: 1 + widget.pulse.value * .012, child: child),
              child: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(36),
                  boxShadow: [BoxShadow(color: violet.withOpacity(.18), blurRadius: 44, spreadRadius: 2)],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(29),
                  child: SizedBox(width: 290, height: 290, child: CoverArt(track: track, size: 290, radius: 29)),
                ),
              ),
            ),
            const SizedBox(height: 35),
            Row(children: [
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(track.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w800, letterSpacing: -.6)),
                const SizedBox(height: 5),
                Text(track.artist, style: const TextStyle(color: Colors.white60, fontSize: 15)),
              ])),
              const Icon(Icons.favorite_border_rounded, color: Colors.white70, size: 27),
            ]),
            const SizedBox(height: 23),
            StreamBuilder<Duration>(
              stream: widget.player.positionStream,
              builder: (context, snap) {
                final pos = snap.data ?? Duration.zero;
                final total = widget.player.duration ?? Duration.zero;
                final max = math.max(1, total.inMilliseconds).toDouble();
                return Column(children: [
                  SliderTheme(
                    data: SliderTheme.of(context).copyWith(
                      trackHeight: 4,
                      activeTrackColor: cyan,
                      inactiveTrackColor: Colors.white12,
                      thumbColor: Colors.white,
                      overlayColor: cyan.withOpacity(.12),
                      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
                    ),
                    child: Slider(
                      min: 0, max: max,
                      value: pos.inMilliseconds.clamp(0, max.toInt()).toDouble(),
                      onChanged: (v) => widget.player.seek(Duration(milliseconds: v.toInt())),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 3),
                    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                      Text(time(pos), style: const TextStyle(color: Colors.white38, fontSize: 11)),
                      Text(time(total), style: const TextStyle(color: Colors.white38, fontSize: 11)),
                    ]),
                  ),
                ]);
              },
            ),
            const SizedBox(height: 13),
            SizedBox(height: 75, child: AnimatedBuilder(
              animation: widget.pulse,
              builder: (_, __) => CustomPaint(
                painter: WavePainter(progress: (widget.player.position.inMilliseconds / math.max(1, (widget.player.duration?.inMilliseconds ?? 1))).clamp(0.0, 1.0), phase: widget.pulse.value),
                size: const Size(double.infinity, 75),
              ),
            )),
            Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
              IconButton(onPressed: () {}, icon: const Icon(Icons.shuffle_rounded, color: Colors.white54)),
              IconButton(onPressed: widget.onPrevious, icon: const Icon(Icons.skip_previous_rounded, size: 38)),
              StreamBuilder<PlayerState>(
                stream: widget.player.playerStateStream,
                builder: (_, snap) {
                  final playing = snap.data?.playing ?? widget.player.playing;
                  return GestureDetector(
                    onTap: () => playing ? widget.player.pause() : widget.player.play(),
                    child: Container(
                      width: 72, height: 72,
                      decoration: const BoxDecoration(shape: BoxShape.circle, gradient: LinearGradient(colors: [violet, cyan])),
                      child: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded, color: ink, size: 38),
                    ),
                  );
                },
              ),
              IconButton(onPressed: widget.onNext, icon: const Icon(Icons.skip_next_rounded, size: 38)),
              IconButton(onPressed: () {}, icon: const Icon(Icons.repeat_rounded, color: Colors.white54)),
            ]),
            const Spacer(),
            const Text('ZVYKMUSIC  •  YOUR SOUND', style: TextStyle(color: Colors.white24, letterSpacing: 2, fontSize: 9)),
          ]),
        )),
      ]),
    );
  }
}

class TrackTile extends StatelessWidget {
  final Track track;
  final int index;
  final bool active;
  final bool playing;
  final VoidCallback onTap;
  const TrackTile({super.key, required this.track, required this.index, required this.active, required this.playing, required this.onTap});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 7),
    child: Material(
      color: active ? Colors.white.withOpacity(.085) : Colors.transparent,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(children: [
            CoverArt(track: track, size: 55, radius: 14),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(track.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: active ? cyan : Colors.white)),
              const SizedBox(height: 4),
              Text('${track.artist}  ·  ${track.album}', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white54, fontSize: 11)),
            ])),
            if (playing) const Icon(Icons.graphic_eq_rounded, color: cyan)
            else Text(track.duration, style: const TextStyle(color: Colors.white38, fontSize: 11)),
            const SizedBox(width: 6),
            Icon(active && playing ? Icons.pause_circle_filled_rounded : Icons.play_circle_outline_rounded, color: active ? cyan : Colors.white60, size: 27),
          ]),
        ),
      ),
    ),
  );
}

class CoverArt extends StatelessWidget {
  final Track track;
  final double size;
  final double radius;
  const CoverArt({super.key, required this.track, required this.size, required this.radius});
  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(radius),
    child: SizedBox(
      width: size, height: size,
      child: track.cover.isEmpty
        ? const ColoredBox(color: Color(0xFF24223A), child: Icon(Icons.music_note_rounded, color: Colors.white54))
        : CachedNetworkImage(
            imageUrl: track.cover,
            fit: BoxFit.cover,
            placeholder: (_, __) => const ColoredBox(color: Color(0xFF24223A), child: Icon(Icons.music_note_rounded, color: Colors.white38)),
            errorWidget: (_, __, ___) => const ColoredBox(color: Color(0xFF24223A), child: Icon(Icons.music_note_rounded, color: Colors.white38)),
          ),
    ),
  );
}

class GlassIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  const GlassIconButton({super.key, required this.icon, required this.onTap});
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white.withOpacity(.07),
    shape: const CircleBorder(),
    child: InkWell(onTap: onTap, customBorder: const CircleBorder(), child: SizedBox(width: 42, height: 42, child: Icon(icon, color: Colors.white70))),
  );
}

class GlassPanel extends StatelessWidget {
  final Widget child;
  final double radius;
  final EdgeInsets padding;
  const GlassPanel({super.key, required this.child, this.radius = 24, this.padding = const EdgeInsets.all(12)});
  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(radius),
    child: BackdropFilter(
      filter: ImageFilter.blur(sigmaX: 22, sigmaY: 22),
      child: Container(
        padding: padding,
        decoration: BoxDecoration(
          color: const Color(0xFF1D1C2C).withOpacity(.68),
          borderRadius: BorderRadius.circular(radius),
          border: Border.all(color: Colors.white.withOpacity(.13)),
          boxShadow: [BoxShadow(color: Colors.black.withOpacity(.16), blurRadius: 24, offset: const Offset(0, 8))],
        ),
        child: child,
      ),
    ),
  );
}

class AmbientBackground extends StatelessWidget {
  const AmbientBackground({super.key});
  @override
  Widget build(BuildContext context) => Stack(children: [
    const ColoredBox(color: ink),
    Positioned(top: -100, left: -90, child: _orb(290, violet.withOpacity(.18))),
    Positioned(top: 250, right: -140, child: _orb(300, cyan.withOpacity(.10))),
    Positioned(bottom: -180, left: 40, child: _orb(330, const Color(0xFF5736B9).withOpacity(.14))),
  ]);
  Widget _orb(double size, Color color) => ImageFiltered(
    imageFilter: ImageFilter.blur(sigmaX: 75, sigmaY: 75),
    child: Container(width: size, height: size, decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
  );
}

class WavePainter extends CustomPainter {
  final double progress;
  final double phase;
  WavePainter({required this.progress, required this.phase});

  @override
  void paint(Canvas canvas, Size size) {
    const bars = 64;
    final gap = 3.0;
    final barWidth = (size.width - gap * (bars - 1)) / bars;
    for (var i = 0; i < bars; i++) {
      final x = i * (barWidth + gap);
      final wave = (math.sin(i * .43 + phase * 2.8).abs() * .55 +
          math.sin(i * .19 + 1.2).abs() * .45);
      final centerBoost = 0.28 + 0.72 * (1 - ((i - bars / 2).abs() / (bars / 2)));
      final h = 8 + wave * centerBoost * (size.height - 12);
      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, (size.height - h) / 2, barWidth, h),
        const Radius.circular(3),
      );
      final p = Paint()
        ..shader = LinearGradient(
          begin: Alignment.bottomCenter,
          end: Alignment.topCenter,
          colors: [violet, i / bars <= progress ? cyan : Colors.white24],
        ).createShader(Rect.fromLTWH(x, 0, barWidth, size.height));
      canvas.drawRRect(rect, p);
    }
  }

  @override
  bool shouldRepaint(covariant WavePainter oldDelegate) =>
      oldDelegate.progress != progress || oldDelegate.phase != phase;
}
