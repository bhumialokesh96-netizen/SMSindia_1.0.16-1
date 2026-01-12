import 'package:flutter/material.dart';
import '../constants/app_colors.dart';

class SpinScreen extends StatelessWidget {
  const SpinScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Lucky Spin'),
      ),
      body: Center(
        child: Text('Spin Wheel Screen - To be implemented'),
      ),
    );
  }
}
