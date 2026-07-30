<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    public function register(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => ['required', 'string', 'max:120'],
            'email' => ['required', 'email', 'max:255', 'unique:users,email'],
            'password' => ['required', 'string', 'min:10', 'confirmed'],
            'device_name' => ['required', 'string', 'max:120'],
        ]);

        $user = User::create([
            'name' => $validated['name'],
            'email' => strtolower($validated['email']),
            'password' => $validated['password'],
        ]);

        return response()->json([
            'data' => [
                'token' => $user->createToken($validated['device_name'])->plainTextToken,
                'user' => $user->only(['id', 'name', 'email']),
            ],
        ], 201);
    }

    public function login(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
            'device_name' => ['required', 'string', 'max:120'],
        ]);

        $user = User::where('email', strtolower($validated['email']))->first();

        if (! $user || ! Hash::check($validated['password'], $user->password)) {
            throw ValidationException::withMessages([
                'email' => ['Las credenciales proporcionadas no son válidas.'],
            ]);
        }

        return response()->json([
            'data' => [
                'token' => $user->createToken($validated['device_name'])->plainTextToken,
                'user' => $user->only(['id', 'name', 'email']),
            ],
        ]);
    }

    /**
     * Perfil del usuario autenticado.
     *
     * Existe porque el nombre solo vivia en las preferencias del telefono: al abrir la
     * sesion en otro dispositivo se veia el del registro y no el que el usuario habia
     * puesto, sin ninguna ruta por la que pudiera viajar.
     */
    public function profile(Request $request): JsonResponse
    {
        return response()->json(['data' => $request->user()->only(['id', 'name', 'email'])]);
    }

    /** Cambia el nombre visible. El correo no se toca: es la credencial de acceso. */
    public function updateProfile(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => ['required', 'string', 'min:1', 'max:120'],
        ]);

        $user = $request->user();
        $user->forceFill(['name' => trim($validated['name'])])->save();

        return response()->json(['data' => $user->only(['id', 'name', 'email'])]);
    }

    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()?->delete();

        return response()->json(['message' => 'Sesión cerrada.']);
    }
}
